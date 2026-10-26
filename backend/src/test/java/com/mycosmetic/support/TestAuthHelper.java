package com.mycosmetic.support;

import java.util.UUID;

import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import com.mycosmetic.entity.User;
import com.mycosmetic.repository.UserRepository;
import com.mycosmetic.security.JwtService;
import com.mycosmetic.security.TokenCookies;

import jakarta.servlet.http.Cookie;

/**
 * 보안 테스트 도우미: 사용자를 만들고 실제 로그인과 같은 access_token 쿠키를 만든다.
 */
@Component
public class TestAuthHelper {

	public static final String PASSWORD = "password123";

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final JwtService jwtService;

	public TestAuthHelper(UserRepository userRepository, PasswordEncoder passwordEncoder, JwtService jwtService) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.jwtService = jwtService;
	}

	/** 테스트끼리 DB를 같이 쓰므로 매번 다른 이메일을 쓴다 */
	public static String uniqueEmail() {
		return "u-" + UUID.randomUUID() + "@test.com";
	}

	/** 비밀번호가 {@link #PASSWORD}인 이메일 회원 */
	public User createUser(String email) {
		return userRepository.save(User.ofEmail(email, passwordEncoder.encode(PASSWORD), "테스터"));
	}

	public Cookie accessTokenCookie(User user) {
		return accessTokenCookie(user.getId());
	}

	public Cookie accessTokenCookie(Long userId) {
		return new Cookie(TokenCookies.ACCESS_TOKEN, jwtService.issueAccessToken(userId));
	}
}
