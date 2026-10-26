package com.mycosmetic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.mycosmetic.entity.Provider;
import com.mycosmetic.entity.User;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.repository.SocialAccountRepository;
import com.mycosmetic.service.auth.SocialAccountService;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.TestAuthHelper;

/** DELETE /api/auth/social/kakao — 보안 테스트 4종 */
class KakaoUnlinkSecurityTest extends IntegrationTestSupport {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TestAuthHelper auth;

	@Autowired
	SocialAccountService socialAccountService;

	@Autowired
	SocialAccountRepository socialAccountRepository;

	private User linkedEmailUser() {
		User user = auth.createUser(TestAuthHelper.uniqueEmail());
		socialAccountService.loginOrRegister("k-" + UUID.randomUUID(), "닉", null, user.getId());
		return user;
	}

	private boolean kakaoLinked(Long userId) {
		return socialAccountRepository.findByUserIdAndProvider(userId, Provider.KAKAO).isPresent();
	}

	@Test
	@DisplayName("로그인하지 않으면 401과 공통 오류 형식")
	void anonymous_returns401() throws Exception {
		mockMvc.perform(delete("/api/auth/social/kakao").with(csrf()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").value(ErrorCode.UNAUTHORIZED.message()))
				.andExpect(jsonPath("$.traceId").value(matchesPattern("[0-9a-f]{8}")));
	}

	@Test
	@DisplayName("CSRF 토큰 없이 보내면 403이고 연결은 그대로")
	void withoutCsrf_returns403() throws Exception {
		User user = linkedEmailUser();

		mockMvc.perform(delete("/api/auth/social/kakao").cookie(auth.accessTokenCookie(user)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CSRF_INVALID"));

		assertThat(kakaoLinked(user.getId())).isTrue();
	}

	@Test
	@DisplayName("B가 해제를 요청해도 A의 연결은 그대로")
	void otherUser_cannotUnlinkMine() throws Exception {
		User a = linkedEmailUser();
		User b = linkedEmailUser();

		mockMvc.perform(delete("/api/auth/social/kakao").with(csrf()).cookie(auth.accessTokenCookie(b)))
				.andExpect(status().isNoContent());

		assertThat(kakaoLinked(a.getId())).isTrue();
		assertThat(kakaoLinked(b.getId())).isFalse();
	}

	@Test
	@DisplayName("비밀번호가 있는 회원은 카카오 연결을 해제한다")
	void owner_unlinks() throws Exception {
		User user = linkedEmailUser();

		mockMvc.perform(delete("/api/auth/social/kakao").with(csrf()).cookie(auth.accessTokenCookie(user)))
				.andExpect(status().isNoContent());

		assertThat(kakaoLinked(user.getId())).isFalse();
	}

	@Test
	@DisplayName("카카오 전용 계정이 해제하면 409 LAST_LOGIN_METHOD이고 연결은 그대로")
	void kakaoOnly_returns409() throws Exception {
		Long userId = socialAccountService.loginOrRegister("k-" + UUID.randomUUID(), "닉", null, null);

		mockMvc.perform(delete("/api/auth/social/kakao").with(csrf()).cookie(auth.accessTokenCookie(userId)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("LAST_LOGIN_METHOD"))
				.andExpect(jsonPath("$.message").value(ErrorCode.LAST_LOGIN_METHOD.message()));

		assertThat(kakaoLinked(userId)).isTrue();
	}
}
