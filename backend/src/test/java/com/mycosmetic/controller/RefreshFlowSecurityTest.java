package com.mycosmetic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.allOf;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.mycosmetic.entity.User;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.TestAuthHelper;

import jakarta.servlet.http.Cookie;

class RefreshFlowSecurityTest extends IntegrationTestSupport {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TestAuthHelper auth;

	@Autowired
	JdbcTemplate jdbcTemplate;

	private ResultActions login(String email) throws Exception {
		return mockMvc.perform(post("/api/auth/login").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"password\":\"%s\"}".formatted(email, TestAuthHelper.PASSWORD)));
	}

	private static Cookie refreshCookie(MockHttpServletResponse response) {
		Cookie cookie = response.getCookie("refresh_token");
		assertThat(cookie).isNotNull();
		return new Cookie("refresh_token", cookie.getValue());
	}

	@Test
	@DisplayName("refresh 쿠키는 HttpOnly, Secure, SameSite=Strict, Path=/api/auth, 14일")
	void refreshCookie_hasDesignAttributes() throws Exception {
		User user = auth.createUser(TestAuthHelper.uniqueEmail());

		login(user.getEmail())
				.andExpect(status().isNoContent())
				.andExpect(header().stringValues(HttpHeaders.SET_COOKIE, hasItem(allOf(
						startsWith("refresh_token="),
						containsString("HttpOnly"),
						containsString("Secure"),
						containsString("SameSite=Strict"),
						containsString("Path=/api/auth;"),
						containsString("Max-Age=1209600")))));
	}

	@Test
	@DisplayName("가입 → 로그인 → 갱신 → 로그아웃 뒤 갱신하면 401")
	void fullFlow_refreshAfterLogoutFails() throws Exception {
		String email = TestAuthHelper.uniqueEmail();
		mockMvc.perform(post("/api/auth/signup").with(csrf())
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"email\":\"%s\",\"password\":\"password123\",\"nickname\":\"새사용자\"}".formatted(email)))
				.andExpect(status().isCreated());
		Cookie first = refreshCookie(login(email).andReturn().getResponse());

		MockHttpServletResponse refreshed = mockMvc.perform(post("/api/auth/refresh").with(csrf()).cookie(first))
				.andExpect(status().isNoContent())
				.andExpect(header().stringValues(HttpHeaders.SET_COOKIE, hasItem(startsWith("access_token="))))
				.andReturn().getResponse();
		Cookie second = refreshCookie(refreshed);
		assertThat(second.getValue()).isNotEqualTo(first.getValue());

		mockMvc.perform(post("/api/auth/logout").with(csrf()).cookie(second))
				.andExpect(status().isNoContent())
				.andExpect(header().stringValues(HttpHeaders.SET_COOKIE, hasItem(allOf(
						startsWith("access_token=;"), containsString("Max-Age=0")))))
				.andExpect(header().stringValues(HttpHeaders.SET_COOKIE, hasItem(allOf(
						startsWith("refresh_token=;"), containsString("Path=/api/auth;"),
						containsString("Max-Age=0")))));

		mockMvc.perform(post("/api/auth/refresh").with(csrf()).cookie(second))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
	}

	@Test
	@DisplayName("훔친 토큰이 먼저 갱신돼도 피해자가 이전 토큰으로 로그아웃하면 새 토큰으로 갱신할 수 없다")
	void logoutWithOldToken_blocksRotatedToken() throws Exception {
		User user = auth.createUser(TestAuthHelper.uniqueEmail());
		Cookie old = refreshCookie(login(user.getEmail()).andReturn().getResponse());
		Cookie stolenRotated = refreshCookie(mockMvc.perform(post("/api/auth/refresh").with(csrf()).cookie(old))
				.andExpect(status().isNoContent())
				.andReturn().getResponse());

		mockMvc.perform(post("/api/auth/logout").with(csrf()).cookie(old))
				.andExpect(status().isNoContent());

		mockMvc.perform(post("/api/auth/refresh").with(csrf()).cookie(stolenRotated))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
	}

	@Test
	@DisplayName("사용자 행이 지워진 뒤(탈퇴) 남은 refresh 쿠키로 갱신하면 401")
	void refreshAfterUserDeleted_returns401() throws Exception {
		User user = auth.createUser(TestAuthHelper.uniqueEmail());
		Cookie refresh = refreshCookie(login(user.getEmail()).andReturn().getResponse());

		jdbcTemplate.update("delete from users where id = ?", user.getId());

		mockMvc.perform(post("/api/auth/refresh").with(csrf()).cookie(refresh))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
	}

	@Test
	@DisplayName("refresh 쿠키 없이 갱신하면 401 REFRESH_TOKEN_INVALID")
	void refreshWithoutCookie_returns401() throws Exception {
		mockMvc.perform(post("/api/auth/refresh").with(csrf()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("REFRESH_TOKEN_INVALID"));
	}

	@Test
	@DisplayName("CSRF 없는 갱신 요청은 403")
	void refreshWithoutCsrf_returns403() throws Exception {
		User user = auth.createUser(TestAuthHelper.uniqueEmail());
		Cookie refresh = refreshCookie(login(user.getEmail()).andReturn().getResponse());

		mockMvc.perform(post("/api/auth/refresh").cookie(refresh))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CSRF_INVALID"));
	}

	@Test
	@DisplayName("CSRF 없는 로그아웃 요청은 403")
	void logoutWithoutCsrf_returns403() throws Exception {
		mockMvc.perform(post("/api/auth/logout"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CSRF_INVALID"));
	}
}
