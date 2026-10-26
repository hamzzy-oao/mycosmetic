package com.mycosmetic.dto.advice;

import java.util.List;

import com.mycosmetic.service.advice.AdviceLevel;
import com.mycosmetic.service.advice.AdviceReason;
import com.mycosmetic.service.advice.ReasonType;

/**
 * 판단 근거 하나.
 *
 * @param ingredients         근거가 된 성분 이름
 * @param relatedShelfItemIds 관련 내 화장대 항목 ID(이름은 응답의 relatedItems에 있다)
 */
public record AdviceReasonResponse(ReasonType type, AdviceLevel level, String message, List<String> ingredients,
		List<Long> relatedShelfItemIds) {

	public static AdviceReasonResponse of(AdviceReason reason) {
		return new AdviceReasonResponse(reason.type(), reason.level(), reason.message(), reason.ingredients(),
				reason.relatedShelfItemIds());
	}
}
