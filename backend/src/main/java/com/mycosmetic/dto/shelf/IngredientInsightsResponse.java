package com.mycosmetic.dto.shelf;

import java.util.List;

/**
 * 맞음·안 맞음 평가로 가려낸 성분(v-8).
 *
 * @param goodCount 맞음으로 평가한 제품 수
 * @param badCount  안 맞음으로 평가한 제품 수
 * @param ready     가려낼 만큼 평가가 있는지(안 맞음 2개, 맞음 1개 이상). false면 두 목록은 비어 있다
 * @param suspects  안 맞음 제품에 자주 있고 맞음 제품에는 드문 성분(많이 나온 순, 최대 10개)
 * @param friendly  맞음 제품에 자주 있고 안 맞음 제품에는 없는 성분(많이 나온 순, 최대 10개)
 * @param rows      맞음·안 맞음 제품에 나온 모든 성분과 각 개수(비교 토글용, 이름순). 평가가 없으면 빈 목록
 */
public record IngredientInsightsResponse(int goodCount, int badCount, boolean ready, List<Insight> suspects,
		List<Insight> friendly, List<Insight> rows) {

	public IngredientInsightsResponse {
		suspects = List.copyOf(suspects);
		friendly = List.copyOf(friendly);
		rows = List.copyOf(rows);
	}

	/** 성분 이름과 그 성분이 든 안 맞음·맞음 제품 수 */
	public record Insight(String name, int badCount, int goodCount) {
	}
}
