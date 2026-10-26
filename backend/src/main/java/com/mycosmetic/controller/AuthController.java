package com.mycosmetic.controller;

import java.time.Clock;

import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.web.csrf.CsrfToken;
import org.springframework.web.bind.annotation.CookieValue;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mycosmetic.dto.auth.EmailAvailabilityResponse;
import com.mycosmetic.dto.auth.LoginRequest;
import com.mycosmetic.dto.auth.SignupRequest;
import com.mycosmetic.security.CurrentUserId;
import com.mycosmetic.security.JwtService;
import com.mycosmetic.security.TokenCookies;
import com.mycosmetic.service.auth.AuthService;
import com.mycosmetic.service.auth.EmailAvailabilityService;
import com.mycosmetic.service.auth.RefreshTokenService;
import com.mycosmetic.service.auth.RefreshTokenService.Rotated;
import com.mycosmetic.service.auth.SocialAccountService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/auth")
public class AuthController {

	private final AuthService authService;
	private final EmailAvailabilityService emailAvailabilityService;
	private final RefreshTokenService refreshTokenService;
	private final SocialAccountService socialAccountService;
	private final JwtService jwtService;
	private final TokenCookies tokenCookies;
	private final Clock clock;

	public AuthController(AuthService authService, EmailAvailabilityService emailAvailabilityService,
			RefreshTokenService refreshTokenService,
			SocialAccountService socialAccountService, JwtService jwtService, TokenCookies tokenCookies, Clock clock) {
		this.authService = authService;
		this.emailAvailabilityService = emailAvailabilityService;
		this.refreshTokenService = refreshTokenService;
		this.socialAccountService = socialAccountService;
		this.jwtService = jwtService;
		this.tokenCookies = tokenCookies;
		this.clock = clock;
	}

	/** 첫 POST(로그인, 가입) 전에 XSRF-TOKEN 쿠키를 받는다. 토큰 값을 읽어야 쿠키가 만들어진다. */
	@GetMapping("/csrf")
	ResponseEntity<Void> csrf(CsrfToken csrfToken) {
		csrfToken.getToken();
		return ResponseEntity.noContent().build();
	}

	@PostMapping("/signup")
	ResponseEntity<Void> signup(@Valid @RequestBody SignupRequest request) {
		authService.signup(request);
		return ResponseEntity.status(HttpStatus.CREATED).build();
	}

	/**
	 * 가입 화면의 이메일 중복 확인(로그인 불필요). 형식이 틀리면 400, IP마다 1분 30번을 넘으면 429.
	 * IP는 server.forward-headers-strategy: framework라 프록시가 넘긴 X-Forwarded-For를 따른다.
	 */
	@GetMapping("/email-available")
	EmailAvailabilityResponse emailAvailable(@RequestParam("email") @NotBlank @Email @Size(max = 255) String email,
			HttpServletRequest request) {
		return new EmailAvailabilityResponse(emailAvailabilityService.isAvailable(email, request.getRemoteAddr()));
	}

	@PostMapping("/login")
	ResponseEntity<Void> login(@Valid @RequestBody LoginRequest request) {
		Long userId = authService.login(request);
		return withTokens(userId, refreshTokenService.issue(userId));
	}

	/** 리프레시 토큰을 새로 바꾸고 access·refresh 쿠키를 다시 준다 */
	@PostMapping("/refresh")
	ResponseEntity<Void> refresh(@CookieValue(name = TokenCookies.REFRESH_TOKEN, required = false) String refreshToken) {
		Rotated rotated = refreshTokenService.rotate(refreshToken, clock.instant());
		return withTokens(rotated.userId(), rotated.newRawToken());
	}

	/** refresh 쿠키가 있으면 그 토큰을 폐기하고 두 쿠키를 지운다 */
	@PostMapping("/logout")
	ResponseEntity<Void> logout(@CookieValue(name = TokenCookies.REFRESH_TOKEN, required = false) String refreshToken) {
		refreshTokenService.revoke(refreshToken, clock.instant());
		return ResponseEntity.noContent()
				.header(HttpHeaders.SET_COOKIE, tokenCookies.clearAccess().toString())
				.header(HttpHeaders.SET_COOKIE, tokenCookies.clearRefresh().toString())
				.build();
	}

	/** 카카오 연결 해제. 비밀번호가 없는 카카오 전용 계정이면 409 LAST_LOGIN_METHOD */
	@DeleteMapping("/social/kakao")
	ResponseEntity<Void> unlinkKakao(@CurrentUserId Long userId) {
		socialAccountService.unlink(userId);
		return ResponseEntity.noContent().build();
	}

	private ResponseEntity<Void> withTokens(Long userId, String refreshToken) {
		return ResponseEntity.noContent()
				.header(HttpHeaders.SET_COOKIE, tokenCookies.access(jwtService.issueAccessToken(userId)).toString())
				.header(HttpHeaders.SET_COOKIE, tokenCookies.refresh(refreshToken).toString())
				.build();
	}
}
