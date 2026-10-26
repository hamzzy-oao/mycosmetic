package com.mycosmetic.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Collections;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;

import com.mycosmetic.security.JwtService;
import com.mycosmetic.security.oauth.KakaoLinkAuthorizationRequestResolver.LinkIntent;
import com.mycosmetic.service.user.UserService;

import jakarta.servlet.http.Cookie;

class KakaoLinkAuthorizationRequestResolverTest {

	private static final String SECRET = "dGVzdC1vbmx5LWp3dC1zZWNyZXQtMzItYnl0ZXMtLS0tLS0tLS0=";
	private static final Instant NOW = Instant.parse("2026-10-22T05:00:00Z");

	JwtService jwtService;
	UserService userService;
	KakaoLinkAuthorizationRequestResolver resolver;

	@BeforeEach
	void setUp() {
		jwtService = new JwtService(SECRET, Clock.systemUTC());
		userService = mock(UserService.class);
		when(userService.exists(anyLong())).thenReturn(true);
		resolver = new KakaoLinkAuthorizationRequestResolver(
				new InMemoryClientRegistrationRepository(KakaoOAuth2Config.kakao("client", "secret")),
				jwtService.decoder(), userService, Clock.fixed(NOW, ZoneOffset.UTC));
	}

	private static MockHttpServletRequest authorizationRequest(boolean link) {
		MockHttpServletRequest request = new MockHttpServletRequest("GET", "/oauth2/authorization/kakao");
		request.setServletPath("/oauth2/authorization/kakao");
		if (link) {
			request.setParameter("link", "true");
		}
		return request;
	}

	private static Object linkAttribute(MockHttpServletRequest request, OAuth2AuthorizationRequest resolved) {
		MockHttpSession session = (MockHttpSession) request.getSession(false);
		return session == null ? null : session.getAttribute("kakao-link:" + resolved.getState());
	}

	@Test
	@DisplayName("link=true + 유효한 로그인이면 state에 묶인 세션 값을 남긴다")
	void linkWithLogin_storesUserIdUnderState() {
		MockHttpServletRequest request = authorizationRequest(true);
		request.setCookies(new Cookie("access_token", jwtService.issueAccessToken(7L)));

		OAuth2AuthorizationRequest resolved = resolver.resolve(request);

		assertThat(resolved).isNotNull();
		assertThat(resolved.getAuthorizationUri()).isEqualTo("https://kauth.kakao.com/oauth/authorize");
		assertThat(linkAttribute(request, resolved)).isEqualTo(new LinkIntent(7L, NOW));
		// 연결 의도·사용자 ID는 카카오로 가는 주소에 넣지 않는다
		assertThat(resolved.getAuthorizationRequestUri()).doesNotContain("link");
	}

	@Test
	@DisplayName("link=true인데 로그인하지 않았으면 일반 로그인으로 처리")
	void linkWithoutLogin_isPlainLogin() {
		MockHttpServletRequest request = authorizationRequest(true);

		OAuth2AuthorizationRequest resolved = resolver.resolve(request);

		assertThat(resolved).isNotNull();
		assertThat(linkAttribute(request, resolved)).isNull();
	}

	@Test
	@DisplayName("link=true라도 위조·만료·탈퇴한 사용자의 토큰이면 연결하지 않는다")
	void linkWithInvalidToken_isPlainLogin() {
		MockHttpServletRequest forged = authorizationRequest(true);
		forged.setCookies(new Cookie("access_token", "a.b.c"));
		assertThat(linkAttribute(forged, resolver.resolve(forged))).isNull();

		JwtService past = new JwtService(SECRET,
				Clock.fixed(Instant.now().minus(Duration.ofHours(1)), ZoneOffset.UTC));
		MockHttpServletRequest expired = authorizationRequest(true);
		expired.setCookies(new Cookie("access_token", past.issueAccessToken(7L)));
		assertThat(linkAttribute(expired, resolver.resolve(expired))).isNull();

		when(userService.exists(9L)).thenReturn(false);
		MockHttpServletRequest withdrawn = authorizationRequest(true);
		withdrawn.setCookies(new Cookie("access_token", jwtService.issueAccessToken(9L)));
		assertThat(linkAttribute(withdrawn, resolver.resolve(withdrawn))).isNull();
	}

	@Test
	@DisplayName("link 없이 로그인 상태로 오면 연결이 아니라 로그인이다")
	void loginWithoutLinkParam_doesNotStore() {
		MockHttpServletRequest request = authorizationRequest(false);
		request.setCookies(new Cookie("access_token", jwtService.issueAccessToken(7L)));

		OAuth2AuthorizationRequest resolved = resolver.resolve(request);

		assertThat(linkAttribute(request, resolved)).isNull();
	}

	@Test
	@DisplayName("콜백에서 연결 사용자 ID는 한 번만 꺼낼 수 있다")
	void consumeLinkUserId_isOneShot() {
		MockHttpServletRequest start = authorizationRequest(true);
		start.setCookies(new Cookie("access_token", jwtService.issueAccessToken(7L)));
		OAuth2AuthorizationRequest resolved = resolver.resolve(start);

		MockHttpServletRequest callback = new MockHttpServletRequest("GET", "/login/oauth2/code/kakao");
		callback.setSession(start.getSession(false));
		callback.setParameter("state", resolved.getState());

		assertThat(KakaoLinkAuthorizationRequestResolver.consumeLinkIntent(callback)).isEqualTo(new LinkIntent(7L, NOW));
		assertThat(KakaoLinkAuthorizationRequestResolver.consumeLinkIntent(callback)).isNull();
		assertThat(Collections.list(start.getSession(false).getAttributeNames())).noneMatch(
				name -> name.startsWith("kakao-link:"));
	}

	@Test
	@DisplayName("다른 state로 오면 연결 사용자 ID를 꺼내지 않는다")
	void consumeLinkUserId_otherState_isNull() {
		MockHttpServletRequest start = authorizationRequest(true);
		start.setCookies(new Cookie("access_token", jwtService.issueAccessToken(7L)));
		resolver.resolve(start);

		MockHttpServletRequest callback = new MockHttpServletRequest("GET", "/login/oauth2/code/kakao");
		callback.setSession(start.getSession(false));
		callback.setParameter("state", "other-state");

		assertThat(KakaoLinkAuthorizationRequestResolver.consumeLinkIntent(callback)).isNull();
	}

	@Test
	@DisplayName("연결 의도는 시작 후 10분까지 유효하고 그 뒤에는 만료")
	void linkIntent_expiresAfterTenMinutes() {
		LinkIntent intent = new LinkIntent(7L, NOW);

		assertThat(intent.isExpiredAt(NOW.plus(Duration.ofMinutes(10)))).isFalse();
		assertThat(intent.isExpiredAt(NOW.plus(Duration.ofMinutes(10)).plusSeconds(1))).isTrue();
	}
}
