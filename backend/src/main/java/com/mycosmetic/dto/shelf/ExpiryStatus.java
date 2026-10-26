package com.mycosmetic.dto.shelf;

/** 개봉 후 사용기한 상태(no-ai-features 설계 D) */
public enum ExpiryStatus {
	/** 사용기한이 지났다 */
	EXPIRED,
	/** 30일 안에 끝난다(오늘이 마지막 날인 경우 포함) */
	EXPIRING,
	OK,
	/** 개봉일이나 사용기한(개월)이 없다 */
	UNKNOWN
}
