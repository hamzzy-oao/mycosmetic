package com.mycosmetic.service.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mycosmetic.entity.User;

class LoginAttemptPolicyTest {

	final LoginAttemptPolicy policy = new LoginAttemptPolicy();
	final Instant now = Instant.parse("2026-10-22T00:00:00Z");

	private User user() {
		return User.ofEmail("a@test.com", "hash", "테스터");
	}

	@Test
	@DisplayName("4번째 실패까지는 잠그지 않고 5번째에 10분 잠근다")
	void locksOnFifthFailure() {
		User user = user();
		for (int i = 0; i < 4; i++) {
			policy.onFailure(user, now);
			assertThat(policy.isLocked(user, now)).isFalse();
		}
		policy.onFailure(user, now);

		assertThat(policy.isLocked(user, now)).isTrue();
		assertThat(user.getLockedUntil()).isEqualTo(now.plus(Duration.ofMinutes(10)));
		assertThat(user.getFailedLoginCount()).isEqualTo(5);
	}

	@Test
	@DisplayName("잠금 시간이 지나면 다시 로그인할 수 있다")
	void unlocksAfterLockDuration() {
		User user = user();
		for (int i = 0; i < 5; i++) {
			policy.onFailure(user, now);
		}

		assertThat(policy.isLocked(user, user.getLockedUntil().minusSeconds(1))).isTrue();
		assertThat(policy.isLocked(user, user.getLockedUntil().plusSeconds(1))).isFalse();
	}

	@Test
	@DisplayName("잠금이 풀린 뒤의 실패는 처음부터 다시 센다")
	void failureAfterExpiredLock_startsOver() {
		User user = user();
		for (int i = 0; i < 5; i++) {
			policy.onFailure(user, now);
		}
		Instant later = user.getLockedUntil().plusSeconds(1);

		policy.onFailure(user, later);

		assertThat(user.getFailedLoginCount()).isEqualTo(1);
		assertThat(policy.isLocked(user, later)).isFalse();
	}

	@Test
	@DisplayName("성공하면 실패 횟수를 0으로")
	void successResetsCount() {
		User user = user();
		policy.onFailure(user, now);
		policy.onFailure(user, now);

		policy.onSuccess(user);

		assertThat(user.getFailedLoginCount()).isZero();
		assertThat(user.getLockedUntil()).isNull();
	}
}
