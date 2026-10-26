package com.mycosmetic.security;

import java.util.Set;

import org.springframework.security.oauth2.server.resource.web.BearerTokenResolver;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;

/**
 * access_token 쿠키에서만 JWT를 읽는다. Authorization 헤더는 무시한다.
 * /api/ 아래가 아닌 요청(화면, 정적 파일)과 인증 없이 부르는 인증 API(로그인, 갱신 등)에서는 토큰을 읽지 않는다.
 * 만료됐거나 탈퇴한 사용자의 쿠키가 남아 있어도 화면, 로그인, 갱신이 401로 막히지 않게 하기 위해서다.
 */
public class CookieBearerTokenResolver implements BearerTokenResolver {

	static final Set<String> PUBLIC_API_PATHS = Set.of("/api/auth/csrf", "/api/auth/signup", "/api/auth/login",
			"/api/auth/refresh", "/api/auth/logout");

	@Override
	public String resolve(HttpServletRequest request) {
		String path = request.getRequestURI().substring(request.getContextPath().length());
		if (!path.startsWith("/api/") || PUBLIC_API_PATHS.contains(path)) {
			return null;
		}
		Cookie[] cookies = request.getCookies();
		if (cookies == null) {
			return null;
		}
		for (Cookie cookie : cookies) {
			if (TokenCookies.ACCESS_TOKEN.equals(cookie.getName())) {
				String value = cookie.getValue();
				return value == null || value.isBlank() ? null : value;
			}
		}
		return null;
	}
}
