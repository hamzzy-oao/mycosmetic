package com.mycosmetic.service.shelf;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mycosmetic.dto.shelf.ExpiryStatus;

/** 개봉 후 사용기한·사용 일수 (no-ai-features 설계 D) */
class ShelfUsageTest {

	private static final LocalDate TODAY = LocalDate.of(2026, 10, 22);

	@Test
	@DisplayName("유통기한 = 개봉일 + 개월, 말일은 plusMonths대로 보정, 값이 없으면 null")
	void expiresOn() {
		assertThat(ShelfUsage.expiresOn(LocalDate.of(2026, 1, 15), 6)).isEqualTo(LocalDate.of(2026, 7, 15));
		assertThat(ShelfUsage.expiresOn(LocalDate.of(2026, 1, 31), 1)).isEqualTo(LocalDate.of(2026, 2, 28));
		assertThat(ShelfUsage.expiresOn(LocalDate.of(2026, 1, 31), 60)).isEqualTo(LocalDate.of(2031, 1, 31));
		assertThat(ShelfUsage.expiresOn(null, 12)).isNull();
		assertThat(ShelfUsage.expiresOn(TODAY, null)).isNull();
	}

	@Test
	@DisplayName("상태: 유통기한 다음 날부터 EXPIRED, 당일~30일 남음은 EXPIRING, 31일 이상 OK, 없으면 UNKNOWN")
	void status() {
		assertThat(ShelfUsage.status(null, TODAY)).isEqualTo(ExpiryStatus.UNKNOWN);
		assertThat(ShelfUsage.status(TODAY.minusDays(1), TODAY)).isEqualTo(ExpiryStatus.EXPIRED);
		assertThat(ShelfUsage.status(TODAY, TODAY)).isEqualTo(ExpiryStatus.EXPIRING);
		assertThat(ShelfUsage.status(TODAY.plusDays(30), TODAY)).isEqualTo(ExpiryStatus.EXPIRING);
		assertThat(ShelfUsage.status(TODAY.plusDays(31), TODAY)).isEqualTo(ExpiryStatus.OK);
	}

	@Test
	@DisplayName("사용 일수: 시작일~종료일(없으면 오늘), 시작일이 없으면 null, 미래 시작은 0")
	void usageDays() {
		assertThat(ShelfUsage.usageDays(null, null, TODAY)).isNull();
		assertThat(ShelfUsage.usageDays(LocalDate.of(2026, 10, 1), null, TODAY)).isEqualTo(21L);
		assertThat(ShelfUsage.usageDays(LocalDate.of(2026, 9, 1), LocalDate.of(2026, 9, 11), TODAY)).isEqualTo(10L);
		assertThat(ShelfUsage.usageDays(TODAY.plusDays(3), null, TODAY)).isZero();
	}
}
