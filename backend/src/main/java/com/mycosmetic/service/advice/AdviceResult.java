package com.mycosmetic.service.advice;

import java.util.List;

/** 규칙 판단 결과. reasons는 WARNING → CAUTION → POSITIVE → INFO 순 */
public record AdviceResult(AdviceVerdict verdict, List<AdviceReason> reasons) {

	public AdviceResult {
		reasons = List.copyOf(reasons);
	}
}
