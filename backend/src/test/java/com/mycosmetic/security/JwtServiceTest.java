package com.mycosmetic.security;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtException;

class JwtServiceTest {

	static final String SECRET = Base64.getEncoder().encodeToString("0123456789abcdef0123456789abcdef".getBytes(StandardCharsets.UTF_8));
	static final String OTHER_SECRET = Base64.getEncoder().encodeToString("fedcba9876543210fedcba9876543210".getBytes(StandardCharsets.UTF_8));

	final JwtService jwtService = new JwtService(SECRET, Clock.systemUTC());

	@Test
	@DisplayName("발급한 토큰의 sub가 userId이고 15분 뒤 만료")
	void issuedToken_hasSubjectAndExpiry() {
		String token = jwtService.issueAccessToken(42L);

		Jwt jwt = jwtService.decoder().decode(token);

		assertThat(jwt.getSubject()).isEqualTo("42");
		assertThat(jwt.getClaimAsString("iss")).isEqualTo("mycosmetic");
		assertThat(Duration.between(jwt.getIssuedAt(), jwt.getExpiresAt())).isEqualTo(Duration.ofMinutes(15));
		assertThat(jwt.getHeaders().get("alg")).hasToString("HS256");
	}

	@Test
	@DisplayName("다른 키로 서명한 토큰과 alg=none 토큰은 거절")
	void rejectsForeignKeyAndAlgNone() {
		String foreign = new JwtService(OTHER_SECRET, Clock.systemUTC()).issueAccessToken(42L);
		assertThatThrownBy(() -> jwtService.decoder().decode(foreign)).isInstanceOf(JwtException.class);

		Base64.Encoder url = Base64.getUrlEncoder().withoutPadding();
		long exp = System.currentTimeMillis() / 1000 + 600;
		String none = url.encodeToString("{\"alg\":\"none\"}".getBytes(StandardCharsets.UTF_8)) + "."
				+ url.encodeToString(("{\"sub\":\"42\",\"iss\":\"mycosmetic\",\"exp\":" + exp + "}")
						.getBytes(StandardCharsets.UTF_8))
				+ ".";
		assertThatThrownBy(() -> jwtService.decoder().decode(none)).isInstanceOf(JwtException.class);
	}

	@Test
	@DisplayName("만료된 토큰은 거절")
	void rejectsExpired() {
		Clock past = Clock.offset(Clock.systemUTC(), Duration.ofMinutes(-30));
		String expired = new JwtService(SECRET, past).issueAccessToken(42L);

		assertThatThrownBy(() -> jwtService.decoder().decode(expired)).isInstanceOf(JwtException.class);
	}

	@Test
	@DisplayName("디코딩한 키가 32바이트보다 짧으면 시작하지 않는다")
	void shortSecret_fails() {
		String shortSecret = Base64.getEncoder().encodeToString("too-short".getBytes(StandardCharsets.UTF_8));

		assertThatThrownBy(() -> new JwtService(shortSecret, Clock.systemUTC()))
				.isInstanceOf(IllegalStateException.class);
	}
}
