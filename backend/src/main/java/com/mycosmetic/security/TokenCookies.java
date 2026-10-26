package com.mycosmetic.security;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * 토큰 쿠키(설계 5.2). access는 SameSite=Lax, Path=/. refresh는 SameSite=Strict, Path=/api/auth
 * (로그아웃 요청에도 refresh 쿠키가 와야 폐기할 수 있다). Secure는 app.cookie.secure(기본 true).
 */
@Component
public class TokenCookies {

	public static final String ACCESS_TOKEN = "access_token";
	public static final String REFRESH_TOKEN = "refresh_token";
	public static final String REFRESH_PATH = "/api/auth";
	public static final Duration REFRESH_TOKEN_TTL = Duration.ofDays(14);

	private final boolean secure;

	public TokenCookies(@Value("${app.cookie.secure:true}") boolean secure) {
		this.secure = secure;
	}

	public ResponseCookie access(String token) {
		return accessBuilder(token).maxAge(JwtService.ACCESS_TOKEN_TTL).build();
	}

	public ResponseCookie refresh(String token) {
		return refreshBuilder(token).maxAge(REFRESH_TOKEN_TTL).build();
	}

	public ResponseCookie clearAccess() {
		return accessBuilder("").maxAge(0).build();
	}

	public ResponseCookie clearRefresh() {
		return refreshBuilder("").maxAge(0).build();
	}

	private ResponseCookie.ResponseCookieBuilder accessBuilder(String value) {
		return ResponseCookie.from(ACCESS_TOKEN, value).httpOnly(true).secure(secure).sameSite("Lax").path("/");
	}

	private ResponseCookie.ResponseCookieBuilder refreshBuilder(String value) {
		return ResponseCookie.from(REFRESH_TOKEN, value).httpOnly(true).secure(secure).sameSite("Strict")
				.path(REFRESH_PATH);
	}
}
