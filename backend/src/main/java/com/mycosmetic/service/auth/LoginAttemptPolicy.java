package com.mycosmetic.service.auth;

import java.time.Duration;
import java.time.Instant;

import org.springframework.stereotype.Component;

import com.mycosmetic.entity.User;

/** 같은 계정 로그인 5회 연속 실패 시 10분 잠금(설계 5.4). */
@Component
public class LoginAttemptPolicy {

	public static final int MAX_FAILURES = 5;
	public static final Duration LOCK_DURATION = Duration.ofMinutes(10);

	public boolean isLocked(User user, Instant now) {
		return user.isLockedAt(now);
	}

	/** 5번째 실패에 locked_until = now + 10분 */
	public void onFailure(User user, Instant now) {
		user.recordLoginFailure(MAX_FAILURES, LOCK_DURATION, now);
	}

	public void onSuccess(User user) {
		user.recordLoginSuccess();
	}
}
