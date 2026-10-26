package com.mycosmetic.service.advice;

import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

import com.mycosmetic.entity.Verdict;

/**
 * 판단에 쓰는 내 화장대 항목 하나.
 *
 * @param ingredients 내가 담은 버전의 성분
 */
public record ShelfEntry(Long shelfItemId, Long productId, Verdict verdict, List<AdviceIngredient> ingredients) {

	public ShelfEntry {
		ingredients = List.copyOf(ingredients);
	}

	Set<String> tokens() {
		return ingredients.stream().map(AdviceIngredient::token).collect(Collectors.toSet());
	}
}
