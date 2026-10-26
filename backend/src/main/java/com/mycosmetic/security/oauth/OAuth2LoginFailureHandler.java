package com.mycosmetic.security.oauth;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 카카오 인증 실패(사용자가 동의 취소, state 불일치, 카카오 장애 등) 뒤 화면으로 보낸다.
 * 로그인이면 /login?error=kakao, 연결 중이었으면 /me?error=kakao. 원인은 로그에 오류 코드만 남긴다.
 */
@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

	private static final Logger log = LoggerFactory.getLogger(OAuth2LoginFailureHandler.class);

	private final String frontendBaseUrl;

	public OAuth2LoginFailureHandler(@Value("${app.frontend-base-url:}") String frontendBaseUrl) {
		this.frontendBaseUrl = frontendBaseUrl == null ? "" : frontendBaseUrl.strip();
	}

	@Override
	public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException exception) throws IOException {
		boolean link = KakaoLinkAuthorizationRequestResolver.consumeLinkIntent(request) != null;
		String code = exception instanceof OAuth2AuthenticationException oauth
				? oauth.getError().getErrorCode()
				: exception.getClass().getSimpleName();
		log.info("카카오 인증 실패 code={} link={}", code, link);
		response.sendRedirect(frontendBaseUrl + (link ? "/me?error=kakao" : "/login?error=kakao"));
	}
}
