package com.mycosmetic.controller;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.mycosmetic.entity.User;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.TestAuthHelper;

/** 알림 API — GET이라 1·4번 */
class NotificationControllerSecurityTest extends IntegrationTestSupport {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TestAuthHelper auth;

	@Test
	@DisplayName("로그인하지 않으면 401")
	void anonymous_401() throws Exception {
		mockMvc.perform(get("/api/notifications"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").value(ErrorCode.UNAUTHORIZED.message()))
				.andExpect(jsonPath("$.traceId").value(matchesPattern("[0-9a-f]{8}")));
	}

	@Test
	@DisplayName("본인: 아무 기록이 없으면 오늘 일기 알림 하나")
	void own_200() throws Exception {
		User user = auth.createUser(TestAuthHelper.uniqueEmail());

		mockMvc.perform(get("/api/notifications").cookie(auth.accessTokenCookie(user)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].type").value("DIARY_TODAY"))
				.andExpect(jsonPath("$.items[0].level").value("INFO"))
				.andExpect(jsonPath("$.items[0].link").value("/diary"));
	}
}
