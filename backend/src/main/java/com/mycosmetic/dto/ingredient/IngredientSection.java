package com.mycosmetic.dto.ingredient;

import java.util.List;

import com.mycosmetic.service.ingredient.MatchResult;

/**
 * 전성분 글의 구역 하나(옵션·호수·세트 구성품)와 사전 매칭 결과.
 *
 * @param label 구역 이름. 구역이 하나뿐이면 null
 * @param items 표기 순서대로의 매칭 결과(position은 구역 안에서 1부터)
 */
public record IngredientSection(String label, List<MatchResult> items) {
	public IngredientSection {
		items = List.copyOf(items);
	}
}
