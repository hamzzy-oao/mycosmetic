package com.mycosmetic.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.mycosmetic.entity.User;
import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.service.auth.RefreshTokenService.Rotated;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.TestAuthHelper;

/** 잠금 동작을 확인하려고 실제 MySQL(mycosmetic_test)로 돌린다 */
class RefreshTokenServiceTest extends IntegrationTestSupport {

	@Autowired
	RefreshTokenService refreshTokenService;

	@Autowired
	TestAuthHelper auth;

	@Autowired
	JdbcTemplate jdbcTemplate;

	private Map<String, Object> row(String rawToken) {
		return jdbcTemplate.queryForMap("select family_id, revoked_at from refresh_tokens where token_hash = ?",
				RefreshTokenService.hash(rawToken));
	}

	private User newUser() {
		return auth.createUser(TestAuthHelper.uniqueEmail());
	}

	@Test
	@DisplayName("갱신하면 새 토큰을 주고 이전 토큰은 revoked_at이 찍힌다, family는 같다")
	void rotate_issuesNewAndRevokesOld() {
		User user = newUser();
		String old = refreshTokenService.issue(user.getId());

		Rotated rotated = refreshTokenService.rotate(old, Instant.now());

		assertThat(rotated.userId()).isEqualTo(user.getId());
		assertThat(rotated.newRawToken()).isNotEqualTo(old);
		assertThat(row(old).get("revoked_at")).isNotNull();
		assertThat(row(rotated.newRawToken()).get("revoked_at")).isNull();
		assertThat(row(rotated.newRawToken()).get("family_id")).isEqualTo(row(old).get("family_id"));
	}

	@Test
	@DisplayName("이미 폐기된 토큰이 오면 같은 family 전체를 폐기하고 REFRESH_TOKEN_INVALID")
	void reuse_revokesWholeFamily() {
		User user = newUser();
		String old = refreshTokenService.issue(user.getId());
		String other = refreshTokenService.issue(user.getId());
		Rotated rotated = refreshTokenService.rotate(old, Instant.now());

		assertThatThrownBy(() -> refreshTokenService.rotate(old, Instant.now()))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.REFRESH_TOKEN_INVALID);

		assertThat(row(rotated.newRawToken()).get("revoked_at")).isNotNull();
		// 다른 기기에서 로그인한 다른 family는 그대로
		assertThat(row(other).get("revoked_at")).isNull();
	}

	@Test
	@DisplayName("만료된 토큰은 REFRESH_TOKEN_INVALID")
	void expired_isInvalid() {
		String raw = refreshTokenService.issue(newUser().getId());

		assertThatThrownBy(() -> refreshTokenService.rotate(raw, Instant.now().plus(Duration.ofDays(15))))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.REFRESH_TOKEN_INVALID);
	}

	@Test
	@DisplayName("없는 토큰과 빈 토큰은 REFRESH_TOKEN_INVALID")
	void unknown_isInvalid() {
		assertThatThrownBy(() -> refreshTokenService.rotate("no-such-token", Instant.now()))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.REFRESH_TOKEN_INVALID);
		assertThatThrownBy(() -> refreshTokenService.rotate(null, Instant.now()))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.REFRESH_TOKEN_INVALID);
	}

	@Test
	@DisplayName("DB에는 원문이 아니라 해시만 있다")
	void storesOnlyHash() {
		String raw = refreshTokenService.issue(newUser().getId());

		Integer byRaw = jdbcTemplate.queryForObject("select count(*) from refresh_tokens where token_hash = ?",
				Integer.class, raw);
		Integer byHash = jdbcTemplate.queryForObject("select count(*) from refresh_tokens where token_hash = ?",
				Integer.class, RefreshTokenService.hash(raw));

		assertThat(byRaw).isZero();
		assertThat(byHash).isEqualTo(1);
		assertThat(RefreshTokenService.hash(raw)).hasSize(64).matches("[0-9a-f]+");
	}

	@Test
	@DisplayName("로그아웃으로 폐기한 토큰으로는 갱신할 수 없다")
	void revoked_cannotRotate() {
		String raw = refreshTokenService.issue(newUser().getId());

		refreshTokenService.revoke(raw, Instant.now());

		assertThat(row(raw).get("revoked_at")).isNotNull();
		assertThatThrownBy(() -> refreshTokenService.rotate(raw, Instant.now()))
				.isInstanceOf(BusinessException.class);
	}

	@Test
	@DisplayName("이미 갱신된 이전 토큰으로 로그아웃해도 같은 계보의 새 토큰까지 폐기하고, 다른 계보는 그대로")
	void logoutWithOldToken_revokesWholeFamily() {
		User user = newUser();
		String old = refreshTokenService.issue(user.getId());
		String otherDevice = refreshTokenService.issue(user.getId());
		Rotated rotated = refreshTokenService.rotate(old, Instant.now());

		refreshTokenService.revoke(old, Instant.now());

		assertThat(row(rotated.newRawToken()).get("revoked_at")).isNotNull();
		assertThat(row(otherDevice).get("revoked_at")).isNull();
		assertThatThrownBy(() -> refreshTokenService.rotate(rotated.newRawToken(), Instant.now()))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.REFRESH_TOKEN_INVALID);
	}

	@Test
	@DisplayName("같은 토큰으로 동시에 2번 갱신하면 하나만 성공한다")
	void concurrentRotate_onlyOneSucceeds() throws Exception {
		String raw = refreshTokenService.issue(newUser().getId());
		ExecutorService pool = Executors.newFixedThreadPool(2);
		CountDownLatch start = new CountDownLatch(1);
		List<Future<Boolean>> results = new ArrayList<>();
		try {
			for (int i = 0; i < 2; i++) {
				results.add(pool.submit(() -> {
					start.await();
					try {
						refreshTokenService.rotate(raw, Instant.now());
						return true;
					} catch (BusinessException e) {
						return false;
					}
				}));
			}
			start.countDown();
			List<Boolean> outcomes = new ArrayList<>();
			for (Future<Boolean> result : results) {
				outcomes.add(result.get(60, TimeUnit.SECONDS));
			}

			assertThat(outcomes).containsExactlyInAnyOrder(true, false);
		} finally {
			pool.shutdownNow();
		}
	}
}
