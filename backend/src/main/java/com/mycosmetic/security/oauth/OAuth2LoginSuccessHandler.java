package com.mycosmetic.security.oauth;

import java.io.IOException;
import java.time.Clock;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.web.authentication.AuthenticationSuccessHandler;
import org.springframework.stereotype.Component;

import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.security.JwtService;
import com.mycosmetic.security.TokenCookies;
import com.mycosmetic.security.oauth.KakaoLinkAuthorizationRequestResolver.LinkIntent;
import com.mycosmetic.service.auth.RefreshTokenService;
import com.mycosmetic.service.auth.SocialAccountService;
import com.mycosmetic.service.user.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 카카오 인증 성공 뒤 처리(설계 5.3).
 * - 로그인·가입: loginOrRegister → access·refresh 쿠키 → {frontend-base-url}/shelf
 * - 연결: 현재 계정에 연결 → {frontend-base-url}/me?linked=1 (이미 로그인한 상태라 토큰을 새로 주지 않는다)
 * - 다른 회원에 연결된 카카오 계정 등 거절: 로그인이면 /login?error=kakao, 연결이면 /me?error=kakao
 * - 연결 시작 뒤 10분이 지났거나, 콜백 때 access_token 쿠키의 사용자가 연결을 시작한 사용자와 다르면(또는 쿠키가 없거나
 *   유효하지 않으면) 연결하지 않고 /me?error=kakao. 세션에 남은 오래된 연결 의도를 다른 사람이 이어 쓰지 못하게 한다
 * 이동할 주소는 설정값과 고정 경로로만 만든다(요청 값으로 주소를 정하지 않아 열린 리다이렉트가 없다).
 */
@Component
public class OAuth2LoginSuccessHandler implements AuthenticationSuccessHandler {

	private static final Logger log = LoggerFactory.getLogger(OAuth2LoginSuccessHandler.class);
	static final String DEFAULT_NICKNAME = "카카오 사용자";
	static final int MAX_NICKNAME_LENGTH = 20;

	private final SocialAccountService socialAccountService;
	private final JwtService jwtService;
	private final RefreshTokenService refreshTokenService;
	private final TokenCookies tokenCookies;
	private final UserService userService;
	private final Clock clock;
	private final String frontendBaseUrl;

	public OAuth2LoginSuccessHandler(SocialAccountService socialAccountService, JwtService jwtService,
			RefreshTokenService refreshTokenService, TokenCookies tokenCookies, UserService userService, Clock clock,
			@Value("${app.frontend-base-url:}") String frontendBaseUrl) {
		this.socialAccountService = socialAccountService;
		this.jwtService = jwtService;
		this.refreshTokenService = refreshTokenService;
		this.tokenCookies = tokenCookies;
		this.userService = userService;
		this.clock = clock;
		this.frontendBaseUrl = frontendBaseUrl == null ? "" : frontendBaseUrl.strip();
	}

	@Override
	public void onAuthenticationSuccess(HttpServletRequest request, HttpServletResponse response,
			Authentication authentication) throws IOException {
		LinkIntent linkIntent = KakaoLinkAuthorizationRequestResolver.consumeLinkIntent(request);
		Long linkUserId = linkIntent == null ? null : linkIntent.userId();
		if (linkIntent != null && !isStillValid(linkIntent, request)) {
			response.sendRedirect(frontendBaseUrl + "/me?error=kakao");
			return;
		}
		OAuth2User kakaoUser = (OAuth2User) authentication.getPrincipal();
		Map<String, Object> attributes = kakaoUser.getAttributes();
		Long userId;
		try {
			userId = socialAccountService.loginOrRegister(kakaoUser.getName(), nickname(attributes),
					email(attributes), linkUserId);
		} catch (BusinessException e) {
			log.info("카카오 로그인 거절 code={} link={}", e.errorCode(), linkUserId != null);
			response.sendRedirect(frontendBaseUrl + (linkUserId != null ? "/me?error=kakao" : "/login?error=kakao"));
			return;
		}
		if (linkUserId != null) {
			response.sendRedirect(frontendBaseUrl + "/me?linked=1");
			return;
		}
		response.addHeader(HttpHeaders.SET_COOKIE,
				tokenCookies.access(jwtService.issueAccessToken(userId)).toString());
		response.addHeader(HttpHeaders.SET_COOKIE, tokenCookies.refresh(refreshTokenService.issue(userId)).toString());
		log.info("카카오 로그인 userId={}", userId);
		response.sendRedirect(frontendBaseUrl + "/shelf");
	}

	/** 시작한 지 10분 안이고, 지금 쿠키로 로그인한 사용자가 연결을 시작한 사용자와 같을 때만 연결한다 */
	private boolean isStillValid(LinkIntent intent, HttpServletRequest request) {
		if (intent.isExpiredAt(clock.instant())) {
			log.info("카카오 연결 거절: 시작 후 {}분 경과", LinkIntent.TTL.toMinutes());
			return false;
		}
		Long current = KakaoLinkAuthorizationRequestResolver.authenticatedUserId(request, jwtService.decoder(),
				userService);
		if (!intent.userId().equals(current)) {
			log.info("카카오 연결 거절: 콜백 시점 로그인 사용자가 시작한 사용자와 다르거나 없음");
			return false;
		}
		return true;
	}

	/** kakao_account.profile.nickname → properties.nickname → 기본값. 최대 20자 */
	static String nickname(Map<String, Object> attributes) {
		String nickname = text(map(map(attributes.get("kakao_account")).get("profile")).get("nickname"));
		if (nickname == null) {
			nickname = text(map(attributes.get("properties")).get("nickname"));
		}
		if (nickname == null) {
			return DEFAULT_NICKNAME;
		}
		if (nickname.codePointCount(0, nickname.length()) > MAX_NICKNAME_LENGTH) {
			nickname = nickname.substring(0, nickname.offsetByCodePoints(0, MAX_NICKNAME_LENGTH));
		}
		return nickname;
	}

	/** 동의해서 받은 이메일만. 카카오가 확인되지 않은 이메일이라고 하면 저장하지 않는다 */
	static String email(Map<String, Object> attributes) {
		Map<?, ?> account = map(attributes.get("kakao_account"));
		if (Boolean.FALSE.equals(account.get("is_email_verified"))) {
			return null;
		}
		return text(account.get("email"));
	}

	private static Map<?, ?> map(Object value) {
		return value instanceof Map<?, ?> map ? map : Map.of();
	}

	private static String text(Object value) {
		return value instanceof String s && !s.isBlank() ? s.strip() : null;
	}
}
