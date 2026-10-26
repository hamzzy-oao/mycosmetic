package com.mycosmetic.service.auth;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class EmailCheckRateLimiterTest {

	final EmailCheckRateLimiter limiter = new EmailCheckRateLimiter();
	final Instant now = Instant.parse("2026-10-23T00:00:00Z");

	@Test
	@DisplayName("1분 안에 30번까지는 통과하고 31번째는 막는다")
	void blocksAfterLimit() {
		for (int i = 0; i < EmailCheckRateLimiter.MAX_PER_WINDOW; i++) {
			assertThat(limiter.tryAcquire("1.1.1.1", now.plusSeconds(i))).isTrue();
		}
		assertThat(limiter.tryAcquire("1.1.1.1", now.plusSeconds(59))).isFalse();
	}

	@Test
	@DisplayName("1분이 지나면 다시 통과하고, IP마다 따로 센다")
	void resetsAfterWindowAndCountsPerKey() {
		for (int i = 0; i <= EmailCheckRateLimiter.MAX_PER_WINDOW; i++) {
			limiter.tryAcquire("1.1.1.1", now);
		}
		assertThat(limiter.tryAcquire("1.1.1.1", now)).isFalse();
		assertThat(limiter.tryAcquire("2.2.2.2", now)).isTrue();
		assertThat(limiter.tryAcquire("1.1.1.1", now.plus(EmailCheckRateLimiter.WINDOW))).isTrue();
	}
}
