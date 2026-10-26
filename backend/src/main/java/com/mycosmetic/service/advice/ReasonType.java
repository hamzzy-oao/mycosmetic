package com.mycosmetic.service.advice;

/** 판단 근거 종류(no-ai-features 설계 F의 1~5번과 기록 부족 안내) */
public enum ReasonType {
	/** 1. 안 맞았던(BAD) 제품 2개 이상에 공통으로 있고 잘 맞았던(GOOD) 제품에는 없는 성분 */
	BAD_INGREDIENT,
	/** 2. 잘 맞았던 제품들과 성분이 겹치는 비율 */
	GOOD_OVERLAP,
	/** 3. 내 피부 상태에 주의할 성분 */
	SKIN_TYPE,
	/** 4. 같은 제품이 이미 내 화장대에 있음 */
	ALREADY_ON_SHELF,
	/** 4. 성분이 80% 이상 겹치는 다른 제품이 내 화장대에 있음 */
	SIMILAR_PRODUCT,
	/** 5. 내 루틴에 넣으면 생길 충돌 경고 */
	ROUTINE_CONFLICT,
	/** 평가 기록(GOOD·BAD)이 2개 미만 */
	NEED_MORE_RECORDS
}
