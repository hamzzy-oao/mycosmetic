package com.mycosmetic.service.shelf;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

import com.mycosmetic.dto.shelf.ExpiryStatus;

/**
 * 개봉 후 사용기한과 사용 일수 계산(no-ai-features 설계 D). 날짜만 다루는 순수 함수.
 */
public final class ShelfUsage {

	/** 이 일수 안에 끝나면 EXPIRING */
	static final int EXPIRING_DAYS = 30;

	private ShelfUsage() {
	}

	/** 개봉일 + 사용기한(개월). 말일 보정은 LocalDate.plusMonths를 따른다(1/31 + 1개월 = 2/28). 값이 하나라도 없으면 null */
	public static LocalDate expiresOn(LocalDate openedOn, Integer paoMonths) {
		if (openedOn == null || paoMonths == null) {
			return null;
		}
		return openedOn.plusMonths(paoMonths);
	}

	/** 유통기한 당일까지는 쓸 수 있다고 본다. 오늘이 유통기한 다음 날부터 EXPIRED */
	public static ExpiryStatus status(LocalDate expiresOn, LocalDate today) {
		if (expiresOn == null) {
			return ExpiryStatus.UNKNOWN;
		}
		if (today.isAfter(expiresOn)) {
			return ExpiryStatus.EXPIRED;
		}
		if (ChronoUnit.DAYS.between(today, expiresOn) <= EXPIRING_DAYS) {
			return ExpiryStatus.EXPIRING;
		}
		return ExpiryStatus.OK;
	}

	/** 시작일부터 종료일(없으면 오늘)까지 일수. 시작일이 없으면 null, 시작일이 미래면 0 */
	public static Long usageDays(LocalDate startedOn, LocalDate endedOn, LocalDate today) {
		if (startedOn == null) {
			return null;
		}
		LocalDate end = endedOn != null ? endedOn : today;
		return Math.max(0, ChronoUnit.DAYS.between(startedOn, end));
	}
}
