package com.mycosmetic.dto.trial;

/** 테스트 상태. 선언 순서가 목록 순서다: 평가를 기다리는 것부터 */
public enum TrialStatus {
	/** 기간이 끝났고 아직 평가하지 않음 */
	READY,
	/** 진행 중 */
	ACTIVE,
	/** 평가를 마침 */
	DONE
}
