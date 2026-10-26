package com.mycosmetic.service.advice;

import java.util.Set;

/**
 * 거의 모든 제품에 들어 있어 판단 근거에서 빼는 기본 성분(no-ai-features 설계 F 기본값).
 * 정제수, 글리세린, 부틸렌글라이콜, 1,2-헥산다이올과 같은 뜻의 영문 INCI 이름.
 */
final class CommonIngredients {

	static final IngredientKeyword KEYWORD = new IngredientKeyword(Set.of(
			"정제수", "물", "water", "aqua",
			"글리세린", "glycerin", "glycerine",
			"부틸렌글라이콜", "butyleneglycol",
			"1,2-헥산다이올", "1,2-hexanediol"), Set.of());

	private CommonIngredients() {
	}

	static boolean isCommon(AdviceIngredient ingredient) {
		return KEYWORD.matchesAny(ingredient.names());
	}
}
