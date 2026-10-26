package com.mycosmetic.dto.notification;

public enum NotificationType {
	/** 쓰는 중인 제품의 사용기한이 지남 */
	EXPIRED,
	/** 30일 안에 사용기한이 끝남 */
	EXPIRING,
	/** 테스트 기간이 끝나 평가를 기다림 */
	TRIAL_READY,
	/** 오늘 피부 일기를 아직 안 씀 */
	DIARY_TODAY
}
