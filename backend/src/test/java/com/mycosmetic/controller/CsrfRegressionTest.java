package com.mycosmetic.controller;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.mycosmetic.entity.User;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.TestAuthHelper;

import jakarta.servlet.http.Cookie;

/**
 * 회귀 테스트: 유효한 access_token 쿠키가 있어도 X-XSRF-TOKEN 없이 보낸 상태 변경 요청은 403 CSRF_INVALID.
 * 리소스 서버의 "Bearer 토큰이 있으면 CSRF 검사 생략" 기본 동작이 되살아나면(SecurityConfig의
 * CSRF_FOR_COOKIE_TOKENS가 빠지거나 바뀌면) 이 테스트가 깨진다.
 */
class CsrfRegressionTest extends IntegrationTestSupport {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TestAuthHelper auth;

	private Cookie loggedIn() {
		User user = auth.createUser(TestAuthHelper.uniqueEmail());
		return auth.accessTokenCookie(user);
	}

	@Test
	@DisplayName("로그인 쿠키만 있고 CSRF 토큰 없는 POST /api/users/me는 403 CSRF_INVALID")
	void cookieAuthenticatedPostWithoutCsrf_returns403() throws Exception {
		mockMvc.perform(post("/api/users/me").cookie(loggedIn()))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CSRF_INVALID"));
	}

	@Test
	@DisplayName("다른 /api 경로의 PUT·PATCH도 CSRF 토큰 없으면 403 CSRF_INVALID")
	void cookieAuthenticatedPutPatchWithoutCsrf_returns403() throws Exception {
		Cookie cookie = loggedIn();
		mockMvc.perform(put("/api/skin-profile").cookie(cookie))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CSRF_INVALID"));
		mockMvc.perform(patch("/api/shelf/1").cookie(cookie))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CSRF_INVALID"));
	}
}
