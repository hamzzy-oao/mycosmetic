package com.mycosmetic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import com.mycosmetic.entity.User;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.repository.UserRepository;
import com.mycosmetic.security.TokenCookies;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.TestAuthHelper;

import jakarta.servlet.http.Cookie;

/**
 * DELETE /api/users/me — 보안 테스트 4종 중 1·2·4번. 경로에 ID가 없고 항상 인증 정보의 사용자만 지우므로
 * 3번(남의 데이터)은 해당 없음. 대신 남의 데이터가 그대로인지는 UserServiceWithdrawTest에서 확인한다.
 */
class WithdrawSecurityTest extends IntegrationTestSupport {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TestAuthHelper auth;

	@Autowired
	UserRepository userRepository;

	private User newUser() {
		return auth.createUser(TestAuthHelper.uniqueEmail());
	}

	@Test
	@DisplayName("로그인하지 않으면 401과 공통 오류 형식")
	void anonymous_returns401() throws Exception {
		mockMvc.perform(delete("/api/users/me").with(csrf()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").value(ErrorCode.UNAUTHORIZED.message()))
				.andExpect(jsonPath("$.traceId").value(matchesPattern("[0-9a-f]{8}")));
	}

	@Test
	@DisplayName("CSRF 토큰 없이 보내면 403이고 사용자는 그대로")
	void withoutCsrf_returns403() throws Exception {
		User user = newUser();

		mockMvc.perform(delete("/api/users/me").cookie(auth.accessTokenCookie(user)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CSRF_INVALID"));

		assertThat(userRepository.existsById(user.getId())).isTrue();
	}

	@Test
	@DisplayName("본인이 탈퇴하면 204, 사용자가 지워지고 access·refresh 쿠키를 지운다")
	void owner_withdraws() throws Exception {
		User user = newUser();
		User other = newUser();

		MvcResult result = mockMvc.perform(delete("/api/users/me").with(csrf()).cookie(auth.accessTokenCookie(user)))
				.andExpect(status().isNoContent())
				.andReturn();

		assertThat(userRepository.existsById(user.getId())).isFalse();
		assertThat(userRepository.existsById(other.getId())).isTrue();
		List<String> setCookies = result.getResponse().getHeaders(HttpHeaders.SET_COOKIE);
		assertThat(setCookies).anySatisfy(c -> assertThat(c).startsWith(TokenCookies.ACCESS_TOKEN + "=;")
				.contains("Max-Age=0").contains("Path=/"));
		assertThat(setCookies).anySatisfy(c -> assertThat(c).startsWith(TokenCookies.REFRESH_TOKEN + "=;")
				.contains("Max-Age=0").contains("Path=" + TokenCookies.REFRESH_PATH));
	}

	@Test
	@DisplayName("afterWithdrawal_oldAccessTokenGets401")
	void afterWithdrawal_oldAccessTokenGets401() throws Exception {
		// Review Focus 3: 탈퇴 전 쿠키(최대 15분 유효)로 🔒 API를 부르면 데이터 없이 401.
		// 계획서는 GET /api/shelf를 예로 들었지만 아직 없는 API라 같은 🔒 조회인 GET /api/users/me로 확인한다.
		User user = newUser();
		Cookie oldAccess = auth.accessTokenCookie(user);
		mockMvc.perform(get("/api/users/me").cookie(oldAccess)).andExpect(status().isOk());

		mockMvc.perform(delete("/api/users/me").with(csrf()).cookie(oldAccess))
				.andExpect(status().isNoContent());

		mockMvc.perform(get("/api/users/me").cookie(oldAccess))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.id").doesNotExist());
		mockMvc.perform(delete("/api/users/me").with(csrf()).cookie(oldAccess))
				.andExpect(status().isUnauthorized());
	}
}
