package com.mycosmetic.security.oauth;

import java.io.Serializable;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.web.DefaultOAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestRedirectFilter;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizationRequestResolver;
import org.springframework.security.oauth2.core.endpoint.OAuth2AuthorizationRequest;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtException;

import com.mycosmetic.security.TokenCookies;
import com.mycosmetic.service.user.UserService;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpSession;

/**
 * 카카오 연결 의도를 OAuth2 state에 묶는다(설계 5.1 "카카오 연결").
 * GET /oauth2/authorization/kakao?link=true 이고 요청의 access_token 쿠키가 유효하면(서명·만료·발급자, 사용자 존재)
 * 만들어진 state를 키로 세션 속성 kakao-link:&lt;state&gt; = {@link LinkIntent}(userId, 시작 시각)를 남긴다.
 * 이 경로는 /api/ 밖이라 리소스 서버(CookieBearerTokenResolver)가 쿠키를 읽지 않으므로 여기서 직접 검증한다.
 * 사용자 ID는 주소나 state 문자열에 넣지 않는다. 콜백에서 {@link #consumeLinkIntent}로 한 번만 꺼내고 지운다.
 * 콜백(성공 처리기)은 시작 뒤 {@link LinkIntent#TTL}이 지났거나 지금 로그인한 사용자가 다르면 연결하지 않는다.
 * link=true인데 로그인하지 않았으면 일반 로그인으로 처리한다.
 */
public class KakaoLinkAuthorizationRequestResolver implements OAuth2AuthorizationRequestResolver {

	private static final Logger log = LoggerFactory.getLogger(KakaoLinkAuthorizationRequestResolver.class);
	static final String SESSION_KEY_PREFIX = "kakao-link:";

	private final OAuth2AuthorizationRequestResolver delegate;
	private final JwtDecoder jwtDecoder;
	private final UserService userService;
	private final Clock clock;

	/** 세션에 남기는 연결 의도. 사용자 ID와 시작 시각 */
	public record LinkIntent(Long userId, Instant startedAt) implements Serializable {

		/** 연결을 시작하고 카카오 콜백까지 허용하는 시간 */
		public static final Duration TTL = Duration.ofMinutes(10);

		public boolean isExpiredAt(Instant now) {
			return now.isAfter(startedAt.plus(TTL));
		}
	}

	public KakaoLinkAuthorizationRequestResolver(ClientRegistrationRepository clientRegistrationRepository,
			JwtDecoder jwtDecoder, UserService userService, Clock clock) {
		this.delegate = new DefaultOAuth2AuthorizationRequestResolver(clientRegistrationRepository,
				OAuth2AuthorizationRequestRedirectFilter.DEFAULT_AUTHORIZATION_REQUEST_BASE_URI);
		this.jwtDecoder = jwtDecoder;
		this.userService = userService;
		this.clock = clock;
	}

	@Override
	public OAuth2AuthorizationRequest resolve(HttpServletRequest request) {
		return rememberLinkIntent(request, delegate.resolve(request));
	}

	@Override
	public OAuth2AuthorizationRequest resolve(HttpServletRequest request, String clientRegistrationId) {
		return rememberLinkIntent(request, delegate.resolve(request, clientRegistrationId));
	}

	private OAuth2AuthorizationRequest rememberLinkIntent(HttpServletRequest request,
			OAuth2AuthorizationRequest authorizationRequest) {
		if (authorizationRequest == null || !"true".equals(request.getParameter("link"))) {
			return authorizationRequest;
		}
		Long userId = authenticatedUserId(request, jwtDecoder, userService);
		if (userId == null) {
			log.info("카카오 연결 요청이지만 로그인 상태가 아니어서 일반 로그인으로 처리");
			return authorizationRequest;
		}
		request.getSession().setAttribute(SESSION_KEY_PREFIX + authorizationRequest.getState(),
				new LinkIntent(userId, clock.instant()));
		return authorizationRequest;
	}

	/**
	 * 요청의 access_token 쿠키가 유효하면(서명·만료·발급자, 사용자 존재) 그 사용자 ID, 아니면 null.
	 * /api/ 밖의 OAuth2 경로는 리소스 서버가 쿠키를 읽지 않으므로 시작·콜백 모두 이것으로 확인한다.
	 */
	public static Long authenticatedUserId(HttpServletRequest request, JwtDecoder jwtDecoder,
			UserService userService) {
		String token = accessToken(request);
		if (token == null) {
			return null;
		}
		try {
			Jwt jwt = jwtDecoder.decode(token);
			Long userId = Long.valueOf(jwt.getSubject());
			return userService.exists(userId) ? userId : null;
		} catch (JwtException | NumberFormatException e) {
			return null;
		}
	}

	private static String accessToken(HttpServletRequest request) {
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		for (Cookie cookie : cookies) {
			if (TokenCookies.ACCESS_TOKEN.equals(cookie.getName()) && cookie.getValue() != null
					&& !cookie.getValue().isBlank()) {
				return cookie.getValue();
			}
		}
		return null;
	}

	/**
	 * 콜백 요청의 state로 연결 의도를 꺼내고 세션에서 지운다(한 번만 쓸 수 있다). 연결 요청이 아니면 null.
	 * 성공·실패 처리기가 부른다. 성공 처리기에 올 때 state는 Spring Security가 세션의 인가 요청과 이미 대조한 값이다.
	 * 만료·사용자 일치 확인은 부르는 쪽(성공 처리기)이 한다.
	 */
	public static LinkIntent consumeLinkIntent(HttpServletRequest request) {
		String state = request.getParameter(OAuth2ParameterNames.STATE);
		HttpSession session = request.getSession(false);
		if (state == null || session == null) {
			return null;
		}
		String key = SESSION_KEY_PREFIX + state;
		Object value = session.getAttribute(key);
		session.removeAttribute(key);
		return value instanceof LinkIntent intent ? intent : null;
	}
}
