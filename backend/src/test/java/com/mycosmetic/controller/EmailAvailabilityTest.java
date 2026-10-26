package com.mycosmetic.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;
import org.springframework.test.web.servlet.request.RequestPostProcessor;

import com.mycosmetic.entity.User;
import com.mycosmetic.service.auth.EmailCheckRateLimiter;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.TestAuthHelper;

/** GET /api/auth/email-available — 로그인 없이 부르는 공개 API. 테스트마다 다른 IP로 보내 한도가 섞이지 않게 한다. */
class EmailAvailabilityTest extends IntegrationTestSupport {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TestAuthHelper auth;

	private static String uniqueIp() {
		ThreadLocalRandom r = ThreadLocalRandom.current();
		return "10.%d.%d.%d".formatted(r.nextInt(256), r.nextInt(256), r.nextInt(1, 255));
	}

	private static RequestPostProcessor from(String ip) {
		return request -> {
			request.setRemoteAddr(ip);
			return request;
		};
	}

	private ResultActions check(String email, String ip) throws Exception {
		return mockMvc.perform(get("/api/auth/email-available").param("email", email).with(from(ip)));
	}

	@Test
	@DisplayName("가입하지 않은 이메일이면 로그인 없이도 available=true")
	void unknownEmail_isAvailable() throws Exception {
		check("free-" + UUID.randomUUID() + "@test.com", uniqueIp())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.available").value(true));
	}

	@Test
	@DisplayName("대소문자만 다른 가입된 이메일이면 available=false")
	void registeredEmail_isTakenIgnoringCase() throws Exception {
		User user = auth.createUser(TestAuthHelper.uniqueEmail());

		check(user.getEmail().toUpperCase(), uniqueIp())
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.available").value(false));
	}

	@Test
	@DisplayName("이메일 형식이 아니거나 비어 있으면 400 INVALID_REQUEST")
	void invalidEmail_returns400() throws Exception {
		String ip = uniqueIp();
		check("not-an-email", ip)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		check("", ip)
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		mockMvc.perform(get("/api/auth/email-available").with(from(ip)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	@DisplayName("같은 IP에서 1분에 30번을 넘게 확인하면 429 TOO_MANY_REQUESTS, 다른 IP는 영향 없음")
	void overLimit_returns429() throws Exception {
		String ip = uniqueIp();
		for (int i = 0; i < EmailCheckRateLimiter.MAX_PER_WINDOW; i++) {
			check("free-" + i + "-" + UUID.randomUUID() + "@test.com", ip).andExpect(status().isOk());
		}

		check("free-" + UUID.randomUUID() + "@test.com", ip)
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("TOO_MANY_REQUESTS"))
				.andExpect(jsonPath("$.message").value("잠시 후 다시 시도해 주세요."));
		check("free-" + UUID.randomUUID() + "@test.com", uniqueIp())
				.andExpect(status().isOk());
	}
}
