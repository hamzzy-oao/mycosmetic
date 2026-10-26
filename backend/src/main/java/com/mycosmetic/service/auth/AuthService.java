package com.mycosmetic.service.auth;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mycosmetic.config.logging.LogMasks;
import com.mycosmetic.dto.auth.LoginRequest;
import com.mycosmetic.dto.auth.SignupRequest;
import com.mycosmetic.entity.User;
import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.repository.UserRepository;

/** 이메일 가입과 로그인(설계 5.1). 토큰 발급은 컨트롤러가 JwtService·RefreshTokenService로 한다. */
@Service
public class AuthService {

	private static final Logger log = LoggerFactory.getLogger(AuthService.class);
	/** BCrypt는 72바이트까지만 본다. 더 긴 비밀번호는 저장하지 않는다 */
	private static final int BCRYPT_MAX_BYTES = 72;

	private final UserRepository userRepository;
	private final PasswordEncoder passwordEncoder;
	private final LoginAttemptPolicy loginAttemptPolicy;
	private final Clock clock;
	/** 없는 이메일·비밀번호 없는 계정에도 BCrypt 비교를 한 번 돌려 응답 시간으로 가입 여부가 드러나지 않게 한다 */
	private final String dummyHash;

	public AuthService(UserRepository userRepository, PasswordEncoder passwordEncoder,
			LoginAttemptPolicy loginAttemptPolicy, Clock clock) {
		this.userRepository = userRepository;
		this.passwordEncoder = passwordEncoder;
		this.loginAttemptPolicy = loginAttemptPolicy;
		this.clock = clock;
		this.dummyHash = passwordEncoder.encode("dummy-password-for-timing-1");
	}

	@Transactional
	public void signup(SignupRequest request) {
		String email = request.email().strip();
		if (request.password().getBytes(StandardCharsets.UTF_8).length > BCRYPT_MAX_BYTES) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST);
		}
		if (userRepository.existsByEmail(email)) {
			throw new BusinessException(ErrorCode.EMAIL_DUPLICATE);
		}
		String hash = passwordEncoder.encode(request.password());
		try {
			userRepository.saveAndFlush(User.ofEmail(email, hash, request.nickname().strip()));
		} catch (DataIntegrityViolationException e) {
			// 같은 이메일 동시 가입: uk_users_email에 걸린 쪽
			throw new BusinessException(ErrorCode.EMAIL_DUPLICATE);
		}
		log.info("이메일 가입 email={}", LogMasks.email(email));
	}

	/**
	 * 성공하면 userId. 같은 사용자의 동시 로그인은 사용자 행 잠금으로 줄 세워 실패 횟수를 정확히 센다.
	 * 실패 횟수 변경이 롤백되지 않도록 BusinessException에서도 커밋한다.
	 */
	@Transactional(noRollbackFor = BusinessException.class)
	public Long login(LoginRequest request) {
		Instant now = clock.instant();
		String email = request.email().strip();
		Optional<User> found = userRepository.findIdByEmail(email).flatMap(userRepository::findWithLockById);
		if (found.isEmpty()) {
			passwordEncoder.matches(request.password(), dummyHash);
			log.info("로그인 실패 email={} reason=unknown", LogMasks.email(email));
			throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
		}
		User user = found.get();
		if (loginAttemptPolicy.isLocked(user, now)) {
			log.info("로그인 거절 userId={} reason=locked", user.getId());
			throw new BusinessException(ErrorCode.ACCOUNT_LOCKED);
		}
		if (!passwordMatches(request.password(), user.getPasswordHash())) {
			loginAttemptPolicy.onFailure(user, now);
			log.info("로그인 실패 userId={} failedCount={}", user.getId(), user.getFailedLoginCount());
			throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);
		}
		loginAttemptPolicy.onSuccess(user);
		return user.getId();
	}

	private boolean passwordMatches(String raw, String hash) {
		if (hash == null) {
			passwordEncoder.matches(raw, dummyHash);
			return false;
		}
		return passwordEncoder.matches(raw, hash);
	}
}
