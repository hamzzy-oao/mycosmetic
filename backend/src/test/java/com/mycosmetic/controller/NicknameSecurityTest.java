package com.mycosmetic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.mycosmetic.entity.User;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.repository.UserRepository;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.TestAuthHelper;

/**
 * PATCH /api/users/me 닉네임 변경 🔒 — 401, CSRF 403, 정상. 경로에 ID가 없어 "남의 데이터" 요청은 만들 수 없으므로
 * 다른 사용자의 닉네임이 그대로인지 DB로 확인한다
 */
class NicknameSecurityTest extends IntegrationTestSupport {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TestAuthHelper auth;

	@Autowired
	UserRepository userRepository;

	private User newUser() {
		return auth.createUser(TestAuthHelper.uniqueEmail());
	}

	private static String body(String nickname) {
		return "{\"nickname\":\"" + nickname + "\"}";
	}

	private String nicknameOf(User user) {
		return userRepository.findById(user.getId()).orElseThrow().getNickname();
	}

	@Test
	@DisplayName("로그인하지 않으면 401")
	void anonymous_401() throws Exception {
		mockMvc.perform(patch("/api/users/me").with(csrf()).contentType(MediaType.APPLICATION_JSON).content(body("새이름")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").value(ErrorCode.UNAUTHORIZED.message()))
				.andExpect(jsonPath("$.traceId").value(matchesPattern("[0-9a-f]{8}")));
	}

	@Test
	@DisplayName("CSRF 토큰이 없으면 403이고 닉네임은 그대로")
	void withoutCsrf_403() throws Exception {
		User user = newUser();

		mockMvc.perform(patch("/api/users/me").cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content(body("새이름")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CSRF_INVALID"));

		assertThat(nicknameOf(user)).isEqualTo("테스터");
	}

	@Test
	@DisplayName("본인 요청은 앞뒤 공백을 지워 저장하고 바뀐 내 정보를 돌려준다. 다른 사용자는 그대로")
	void own_200_othersUnchanged() throws Exception {
		User user = newUser();
		User other = newUser();

		mockMvc.perform(patch("/api/users/me").with(csrf()).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content(body("  새 닉네임  ")))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(user.getId()))
				.andExpect(jsonPath("$.email").value(user.getEmail()))
				.andExpect(jsonPath("$.nickname").value("새 닉네임"))
				.andExpect(jsonPath("$.hasPassword").value(true));

		assertThat(nicknameOf(user)).isEqualTo("새 닉네임");
		assertThat(nicknameOf(other)).isEqualTo("테스터");
	}

	@Test
	@DisplayName("공백뿐이거나 21자 이상이면 400이고 닉네임은 그대로")
	void invalid_400() throws Exception {
		User user = newUser();
		var cookie = auth.accessTokenCookie(user);

		for (String bad : new String[] { "   ", "", "가".repeat(21) }) {
			mockMvc.perform(patch("/api/users/me").with(csrf()).cookie(cookie)
					.contentType(MediaType.APPLICATION_JSON).content(body(bad)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		}
		mockMvc.perform(patch("/api/users/me").with(csrf()).cookie(cookie)
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest());

		assertThat(nicknameOf(user)).isEqualTo("테스터");
	}
}
