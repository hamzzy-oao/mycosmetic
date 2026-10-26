package com.mycosmetic.service.extraction;

import java.util.List;

import com.mycosmetic.service.ingredient.MatchResult;

/**
 * extraction_jobs.result_json의 모양. 화면(GET /api/extractions/{jobId}의 result)이 그대로 쓴다.
 *
 * @param ingredientSectionFound AI가 전성분 영역을 찾았는지
 * @param unreadableParts        AI가 읽지 못한 부분의 위치 설명
 * @param warning                null, {@link #NO_INGREDIENT_SECTION}, {@link #MANY_UNMATCHED}
 * @param items                  전성분 순서대로의 사전 매칭 결과
 */
public record ExtractionJobResult(boolean ingredientSectionFound, List<String> unreadableParts, String warning,
		List<MatchResult> items) {

	/** 전성분 영역을 찾지 못함 → 화면에 촬영 팁(한도는 차감) */
	public static final String NO_INGREDIENT_SECTION = "NO_INGREDIENT_SECTION";
	/** UNMATCHED가 30% 이상 → 화면에 "다시 찍을까요?"(한도는 차감) */
	public static final String MANY_UNMATCHED = "MANY_UNMATCHED";

	public ExtractionJobResult {
		unreadableParts = unreadableParts == null ? List.of() : List.copyOf(unreadableParts);
		items = items == null ? List.of() : List.copyOf(items);
	}
}
