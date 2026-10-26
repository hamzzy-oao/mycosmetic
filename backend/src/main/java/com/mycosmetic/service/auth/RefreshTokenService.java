package com.mycosmetic.service.auth;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.UUID;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mycosmetic.entity.RefreshToken;
import com.mycosmetic.entity.User;
import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.repository.RefreshTokenRepository;
import com.mycosmetic.repository.UserRepository;
import com.mycosmetic.security.TokenCookies;

/**
 * 리프레시 토큰 발급, 로테이션, 폐기(설계 5.2).
 * 원문은 32바이트 SecureRandom을 Base64URL로 만든 값이고, DB에는 SHA-256 16진수 해시만 저장한다.
 * 이미 폐기된 토큰이 다시 오면(재사용) 같은 family를 모두 폐기한다.
 * 없음, 만료, 재사용은 모두 같은 REFRESH_TOKEN_INVALID로 응답하고 구분은 로그에만 남긴다.
 */
@Service
public class RefreshTokenService {

	private static final Logger log = LoggerFactory.getLogger(RefreshTokenService.class);
	private static final int TOKEN_BYTES = 32;

	public record Rotated(Long userId, String newRawToken) {
	}

	private final RefreshTokenRepository refreshTokenRepository;
	private final UserRepository userRepository;
	private final Clock clock;
	private final SecureRandom secureRandom = new SecureRandom();

	public RefreshTokenService(RefreshTokenRepository refreshTokenRepository, UserRepository userRepository,
			Clock clock) {
		this.refreshTokenRepository = refreshTokenRepository;
		this.userRepository = userRepository;
		this.clock = clock;
	}

	/** 로그인할 때 새 family로 발급한다. 반환값은 쿠키에 넣을 원문 */
	@Transactional
	public String issue(Long userId) {
		return issueInFamily(userRepository.getReferenceById(userId), UUID.randomUUID().toString(), clock.instant());
	}

	/**
	 * 토큰 행을 비관적 잠금으로 읽어 같은 토큰의 동시 갱신을 줄 세운다.
	 * 재사용 감지 때 family 폐기가 롤백되지 않도록 BusinessException에서도 커밋한다.
	 */
	@Transactional(noRollbackFor = BusinessException.class)
	public Rotated rotate(String rawToken, Instant now) {
		if (rawToken == null || rawToken.isBlank()) {
			throw invalid("missing");
		}
		RefreshToken token = refreshTokenRepository.findByTokenHash(hash(rawToken))
				.orElseThrow(() -> invalid("unknown"));
		if (token.isRevoked()) {
			int revoked = refreshTokenRepository.revokeFamily(token.getFamilyId(), now);
			log.warn("리프레시 토큰 재사용 감지 userId={} familyId={} revokedCount={}", token.getUser().getId(),
					token.getFamilyId(), revoked);
			throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
		}
		if (token.isExpiredAt(now)) {
			throw invalid("expired");
		}
		token.revoke(now);
		String newRawToken = issueInFamily(token.getUser(), token.getFamilyId(), now);
		return new Rotated(token.getUser().getId(), newRawToken);
	}

	/**
	 * 로그아웃: 그 토큰의 계보(family) 전체를 폐기한다. 내 토큰이 이미 폐기됐어도(훔친 사람이 먼저 갱신한 경우)
	 * 같은 계보에서 새로 발급된 토큰까지 막기 위해서다. 다른 기기의 로그인(다른 계보)은 그대로 둔다.
	 * 없는 토큰이면 아무것도 하지 않는다.
	 */
	@Transactional
	public void revoke(String rawToken, Instant now) {
		if (rawToken == null || rawToken.isBlank()) {
			return;
		}
		refreshTokenRepository.findByTokenHash(hash(rawToken)).ifPresent(token -> {
			int revoked = refreshTokenRepository.revokeFamily(token.getFamilyId(), now);
			log.info("로그아웃 userId={} revokedCount={}", token.getUser().getId(), revoked);
		});
	}

	private String issueInFamily(User user, String familyId, Instant now) {
		byte[] bytes = new byte[TOKEN_BYTES];
		secureRandom.nextBytes(bytes);
		String rawToken = Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
		refreshTokenRepository.save(
				RefreshToken.issue(user, hash(rawToken), familyId, now.plus(TokenCookies.REFRESH_TOKEN_TTL)));
		return rawToken;
	}

	private static BusinessException invalid(String reason) {
		log.info("리프레시 토큰 거절 reason={}", reason);
		return new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);
	}

	/** SHA-256 16진수(64자). DB의 token_hash */
	static String hash(String rawToken) {
		try {
			MessageDigest digest = MessageDigest.getInstance("SHA-256");
			return HexFormat.of().formatHex(digest.digest(rawToken.getBytes(StandardCharsets.UTF_8)));
		} catch (NoSuchAlgorithmException e) {
			throw new IllegalStateException(e);
		}
	}
}
