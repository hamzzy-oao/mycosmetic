package com.mycosmetic.service.advice;

import java.util.List;

/**
 * 판단 근거 하나.
 *
 * @param ingredients         근거가 된 대상 제품의 성분 이름(표준명, 없으면 원문)
 * @param relatedShelfItemIds 근거와 관련된 내 화장대 항목 ID
 */
public record AdviceReason(ReasonType type, AdviceLevel level, String message, List<String> ingredients,
		List<Long> relatedShelfItemIds) {

	public AdviceReason {
		ingredients = List.copyOf(ingredients);
		relatedShelfItemIds = List.copyOf(relatedShelfItemIds);
	}
}
