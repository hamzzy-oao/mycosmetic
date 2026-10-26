package com.mycosmetic.dto.ingredient;

import java.util.List;

/** 붙여넣은 전성분을 구역별로 나눠 사전과 맞춘 결과. 성분을 하나도 못 찾으면 빈 목록 */
public record IngredientTextMatchResponse(List<IngredientSection> sections) {
	public IngredientTextMatchResponse {
		sections = List.copyOf(sections);
	}
}
