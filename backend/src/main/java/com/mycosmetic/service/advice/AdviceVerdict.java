package com.mycosmetic.service.advice;

/** "이거 사도 돼?" 판정(no-ai-features 설계 F) */
public enum AdviceVerdict {
	/** 주의할 근거가 없고 잘 맞았던 제품과 성분이 50% 이상 겹친다 */
	GOOD_FIT,
	/** 안 맞았던 성분 1개 또는 피부 상태 주의 성분이 있다 */
	CAUTION,
	/** 안 맞았던 성분이 2개 이상 있다 */
	AVOID,
	/** 판단할 근거가 부족하다(기록이 2개 미만이거나 겹침이 적다) */
	UNKNOWN
}
