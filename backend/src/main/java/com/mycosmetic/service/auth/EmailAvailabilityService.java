package com.mycosmetic.service.auth;

import java.time.Clock;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.repository.UserRepository;

/**
 * 가입 화면의 이메일 중복 확인. 가입 409(EMAIL_DUPLICATE)처럼 가입 여부를 알려 주므로
 * IP마다 요청 수를 제한한다. users.email collation이 대소문자를 구분하지 않아 같은 기준으로 찾는다.
 */
@Service
public class EmailAvailabilityService {

	private final UserRepository userRepository;
	private final EmailCheckRateLimiter rateLimiter;
	private final Clock clock;

	public EmailAvailabilityService(UserRepository userRepository, EmailCheckRateLimiter rateLimiter, Clock clock) {
		this.userRepository = userRepository;
		this.rateLimiter = rateLimiter;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public boolean isAvailable(String email, String clientIp) {
		if (!rateLimiter.tryAcquire(clientIp, clock.instant())) {
			throw new BusinessException(ErrorCode.TOO_MANY_REQUESTS);
		}
		return !userRepository.existsByEmail(email.strip());
	}
}
