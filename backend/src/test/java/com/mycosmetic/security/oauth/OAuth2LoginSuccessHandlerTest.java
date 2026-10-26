package com.mycosmetic.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.TestingAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;

import com.mycosmetic.security.JwtService;
import com.mycosmetic.security.TokenCookies;
import com.mycosmetic.security.oauth.KakaoLinkAuthorizationRequestResolver.LinkIntent;
import com.mycosmetic.service.auth.RefreshTokenService;
import com.mycosmetic.service.auth.SocialAccountService;
import com.mycosmetic.service.user.UserService;

import jakarta.servlet.http.Cookie;

/** 카카오 사용자 정보에서 닉네임·이메일을 꺼내는 규칙과 연결 의도의 만료 */
class OAuth2LoginSuccessHandlerTest {

	private static final String SECRET = "dGVzdC1vbmx5LWp3dC1zZWNyZXQtMzItYnl0ZXMtLS0tLS0tLS0=";

	/** 연결을 시작하고 elapsed가 지난 뒤 같은 사용자의 유효한 쿠키로 콜백이 온다 */
	private static SocialAccountService callbackAfter(Duration elapsed, MockHttpServletResponse response)
			throws Exception {
		Instant started = Instant.now();
		JwtService jwtService = new JwtService(SECRET, Clock.systemUTC());
		UserService userService = mock(UserService.class);
		when(userService.exists(anyLong())).thenReturn(true);
		SocialAccountService socialAccountService = mock(SocialAccountService.class);
		when(socialAccountService.loginOrRegister(anyString(), anyString(), any(), any())).thenReturn(7L);
		OAuth2LoginSuccessHandler handler = new OAuth2LoginSuccessHandler(socialAccountService, jwtService,
				mock(RefreshTokenService.class), new TokenCookies(true), userService,
				Clock.fixed(started.plus(elapsed), ZoneOffset.UTC), "");

		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/login/oauth2/code/kakao");
		request.setParameter("state", "s1");
		request.getSession().setAttribute("kakao-link:s1", new LinkIntent(7L, started));
		request.setCookies(new Cookie(TokenCookies.ACCESS_TOKEN, jwtService.issueAccessToken(7L)));
		DefaultOAuth2User kakaoUser = new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("OAUTH2_USER")),
				Map.of("id", "k-1"), "id");

		handler.onAuthenticationSuccess(request, response, new TestingAuthenticationToken(kakaoUser, null));
		return socialAccountService;
	}

	@Test
	@DisplayName("연결을 시작하고 9분 뒤 콜백이면 시작한 사용자에 연결한다")
	void linkWithinTtl_links() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		SocialAccountService socialAccountService = callbackAfter(Duration.ofMinutes(9), response);

		assertThat(response.getRedirectedUrl()).isEqualTo("/me?linked=1");
		verify(socialAccountService).loginOrRegister(eq("k-1"), anyString(), any(), eq(7L));
	}

	@Test
	@DisplayName("연결을 시작하고 11분 뒤 콜백이면 연결하지 않고 /me?error=kakao")
	void linkAfterTtl_doesNotLink() throws Exception {
		MockHttpServletResponse response = new MockHttpServletResponse();

		SocialAccountService socialAccountService = callbackAfter(Duration.ofMinutes(11), response);

		assertThat(response.getRedirectedUrl()).isEqualTo("/me?error=kakao");
		verify(socialAccountService, never()).loginOrRegister(anyString(), anyString(), any(), any());
		assertThat(response.getHeaders("Set-Cookie")).isEmpty();
	}

	@Test
	@DisplayName("닉네임은 kakao_account.profile → properties → 기본값 순서로, 20자까지")
	void nickname() {
		assertThat(OAuth2LoginSuccessHandler.nickname(Map.of("kakao_account",
				Map.of("profile", Map.of("nickname", "프로필닉")), "properties", Map.of("nickname", "속성닉"))))
				.isEqualTo("프로필닉");
		assertThat(OAuth2LoginSuccessHandler.nickname(Map.of("properties", Map.of("nickname", "속성닉"))))
				.isEqualTo("속성닉");
		assertThat(OAuth2LoginSuccessHandler.nickname(Map.of("id", 1))).isEqualTo("카카오 사용자");
		assertThat(OAuth2LoginSuccessHandler.nickname(Map.of("properties", Map.of("nickname", "가".repeat(30)))))
				.hasSize(20);
	}

	@Test
	@DisplayName("이메일은 받은 경우만, 카카오가 확인되지 않았다고 하면 저장하지 않는다")
	void email() {
		assertThat(OAuth2LoginSuccessHandler.email(Map.of("kakao_account",
				Map.of("email", "a@kakao.com", "is_email_verified", true)))).isEqualTo("a@kakao.com");
		assertThat(OAuth2LoginSuccessHandler.email(Map.of("kakao_account",
				Map.of("email", "a@kakao.com", "is_email_verified", false)))).isNull();
		assertThat(OAuth2LoginSuccessHandler.email(Map.of("id", 1))).isNull();
	}
}
