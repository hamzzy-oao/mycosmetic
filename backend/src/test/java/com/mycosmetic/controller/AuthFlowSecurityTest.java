package com.mycosmetic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.security.test.web.support.WebTestUtils;
import org.springframework.security.web.csrf.CookieCsrfTokenRepository;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.jayway.jsonpath.JsonPath;
import com.mycosmetic.entity.User;
import com.mycosmetic.repository.UserRepository;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.TestAuthHelper;

import jakarta.servlet.http.Cookie;

class AuthFlowSecurityTest extends IntegrationTestSupport {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TestAuthHelper auth;

	@Autowired
	UserRepository userRepository;

	private static String loginJson(String email, String password) {
		return "{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, password);
	}

	private static String signupJson(String email, String password) {
		return "{\"email\":\"%s\",\"password\":\"%s\",\"nickname\":\"새사용자\"}".formatted(email, password);
	}

	/**
	 * 테스트의 csrf() 후처리기는 공유 CsrfFilter의 저장소를 세션 저장소로 바꿔 둔다.
	 * 실제 쿠키 동작을 확인할 때는 운영과 같은 CookieCsrfTokenRepository로 되돌린다.
	 */
	private static RequestPostProcessor realCsrfRepository() {
		return request -> {
			WebTestUtils.setCsrfTokenRepository(request, CookieCsrfTokenRepository.withHttpOnlyFalse());
			return request;
		};
	}

	private ResultActions login(String email, String password) throws Exception {
		return mockMvc.perform(post("/api/auth/login").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginJson(email, password)));
	}

	@Test
	@DisplayName("CSRF 토큰 주소는 204와 XSRF-TOKEN 쿠키를 준다")
	void csrfEndpoint_setsCookie() throws Exception {
		mockMvc.perform(get("/api/auth/csrf").with(realCsrfRepository()))
				.andExpect(status().isNoContent())
				.andExpect(header().stringValues(HttpHeaders.SET_COOKIE, hasItem(startsWith("XSRF-TOKEN="))));
	}

	@Test
	@DisplayName("가입 → 로그인하면 access_token 쿠키가 설계 속성으로 온다")
	void signupThenLogin_setsAccessCookie() throws Exception {
		String email = TestAuthHelper.uniqueEmail();
		mockMvc.perform(post("/api/auth/signup").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(signupJson(email, "password123")))
				.andExpect(status().isCreated());

		login(email, "password123")
				.andExpect(status().isNoContent())
				.andExpect(header().stringValues(HttpHeaders.SET_COOKIE, hasItem(allOf(
						startsWith("access_token="),
						containsString("HttpOnly"),
						containsString("Secure"),
						containsString("SameSite=Lax"),
						containsString("Path=/;"),
						containsString("Max-Age=900")))));
	}

	@Test
	@DisplayName("대소문자만 다른 같은 이메일로 다시 가입하면 409 EMAIL_DUPLICATE")
	void duplicateSignup_returns409() throws Exception {
		User user = auth.createUser(TestAuthHelper.uniqueEmail());

		mockMvc.perform(post("/api/auth/signup").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(signupJson(user.getEmail().toUpperCase(), "password123")))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EMAIL_DUPLICATE"));
	}

	@Test
	@DisplayName("규칙에 맞지 않는 비밀번호로 가입하면 400 INVALID_REQUEST")
	void weakPassword_returns400() throws Exception {
		mockMvc.perform(post("/api/auth/signup").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content(signupJson(TestAuthHelper.uniqueEmail(), "onlyletters")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	@DisplayName("CSRF 토큰 없는 로그인 요청은 403 CSRF_INVALID")
	void loginWithoutCsrf_returns403() throws Exception {
		User user = auth.createUser(TestAuthHelper.uniqueEmail());

		mockMvc.perform(post("/api/auth/login")
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginJson(user.getEmail(), TestAuthHelper.PASSWORD)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CSRF_INVALID"))
				.andExpect(jsonPath("$.traceId").exists());
	}

	@Test
	@DisplayName("5번 틀리면 6번째는 맞는 비밀번호여도 423 ACCOUNT_LOCKED")
	void fiveFailures_thenLocked() throws Exception {
		User user = auth.createUser(TestAuthHelper.uniqueEmail());
		for (int i = 0; i < 5; i++) {
			login(user.getEmail(), "wrong1234")
					.andExpect(status().isUnauthorized())
					.andExpect(jsonPath("$.code").value("INVALID_CREDENTIALS"));
		}

		login(user.getEmail(), TestAuthHelper.PASSWORD)
				.andExpect(status().isLocked())
				.andExpect(jsonPath("$.code").value("ACCOUNT_LOCKED"));
	}

	@Test
	@DisplayName("동시에 10번 틀린 로그인을 보내도 5번째에서 정확히 잠긴다")
	void concurrentWrongLogins_lockExactlyAtFifth() throws Exception {
		User user = auth.createUser(TestAuthHelper.uniqueEmail());
		int threads = 10;
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<String>> results = new ArrayList<>();
		try {
			for (int i = 0; i < threads; i++) {
				results.add(pool.submit(() -> {
					start.await();
					String body = login(user.getEmail(), "wrong1234").andReturn().getResponse().getContentAsString();
					return JsonPath.read(body, "$.code");
				}));
			}
			start.countDown();
			List<String> codes = new ArrayList<>();
			for (Future<String> result : results) {
				codes.add(result.get(60, TimeUnit.SECONDS));
			}

			assertThat(codes).filteredOn(c -> c.equals("INVALID_CREDENTIALS")).hasSize(5);
			assertThat(codes).filteredOn(c -> c.equals("ACCOUNT_LOCKED")).hasSize(5);
		} finally {
			pool.shutdownNow();
		}
		User reloaded = userRepository.findById(user.getId()).orElseThrow();
		assertThat(reloaded.getFailedLoginCount()).isEqualTo(5);
		assertThat(reloaded.getLockedUntil()).isNotNull();
	}

	@Test
	@DisplayName("Authorization 헤더의 Bearer 토큰은 무시한다")
	void bearerHeader_isIgnored() throws Exception {
		User user = auth.createUser(TestAuthHelper.uniqueEmail());
		String token = auth.accessTokenCookie(user).getValue();

		mockMvc.perform(get("/api/users/me").header(HttpHeaders.AUTHORIZATION, "Bearer " + token))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	@DisplayName("잘못된 access_token 쿠키가 남아 있어도 로그인 API는 막히지 않는다")
	void invalidAccessCookie_doesNotBlockLogin() throws Exception {
		User user = auth.createUser(TestAuthHelper.uniqueEmail());

		mockMvc.perform(post("/api/auth/login").with(csrf())
				.cookie(new Cookie("access_token", "garbage"))
				.contentType(MediaType.APPLICATION_JSON)
				.content(loginJson(user.getEmail(), TestAuthHelper.PASSWORD)))
				.andExpect(status().isNoContent());
	}
}
