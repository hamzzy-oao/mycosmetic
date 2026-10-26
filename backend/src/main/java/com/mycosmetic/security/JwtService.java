package com.mycosmetic.security;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Component;

/**
 * access_token(JWT, HS256) 발급과 검증. sub = userId, iss = mycosmetic, 15분.
 * 서명 키는 JWT_SECRET(Base64)이고 디코딩한 길이가 32바이트보다 짧으면 시작하지 않는다.
 */
@Component
public class JwtService {

	public static final String ISSUER = "mycosmetic";
	public static final Duration ACCESS_TOKEN_TTL = Duration.ofMinutes(15);
	private static final int MIN_KEY_BYTES = 32;

	private final JwtEncoder encoder;
	private final NimbusJwtDecoder decoder;
	private final Clock clock;

	@Autowired
	public JwtService(@Value("${app.jwt.secret}") String base64Secret, Clock clock) {
		SecretKey key = toKey(base64Secret);
		this.encoder = NimbusJwtEncoder.withSecretKey(key).algorithm(MacAlgorithm.HS256).build();
		this.decoder = NimbusJwtDecoder.withSecretKey(key).macAlgorithm(MacAlgorithm.HS256).build();
		// 서명·만료(기본 60초 오차 허용)와 함께 발급자를 확인한다
		this.decoder.setJwtValidator(JwtValidators.createDefaultWithIssuer(ISSUER));
		this.clock = clock;
	}

	private static SecretKey toKey(String base64Secret) {
		if (base64Secret == null || base64Secret.isBlank()) {
			throw new IllegalStateException("JWT_SECRET이 비어 있다");
		}
		byte[] bytes;
		try {
			bytes = Base64.getDecoder().decode(base64Secret.strip());
		} catch (IllegalArgumentException e) {
			throw new IllegalStateException("JWT_SECRET은 Base64여야 한다");
		}
		if (bytes.length < MIN_KEY_BYTES) {
			throw new IllegalStateException("JWT_SECRET은 디코딩 후 32바이트 이상이어야 한다");
		}
		return new SecretKeySpec(bytes, "HmacSHA256");
	}

	public String issueAccessToken(Long userId) {
		Instant now = clock.instant();
		JwtClaimsSet claims = JwtClaimsSet.builder()
				.issuer(ISSUER)
				.subject(String.valueOf(userId))
				.issuedAt(now)
				.expiresAt(now.plus(ACCESS_TOKEN_TTL))
				.build();
		JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).build();
		return encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
	}

	/** 리소스 서버가 쓰는 검증기. HS256만 받는다(alg=none·다른 알고리즘 거절). */
	public JwtDecoder decoder() {
		return decoder;
	}
}
