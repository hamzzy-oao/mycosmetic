package com.mycosmetic.service.advice;

import java.util.Collection;
import java.util.Set;

import com.mycosmetic.service.ingredient.IngredientNameNormalizer;

/**
 * 성분 이름 키워드 판정. 이름을 공통 정규화한 뒤 공백과 마침표를 뺀 문자열로 비교한다
 * ("Alcohol Denat." → "alcoholdenat").
 *
 * @param exact    이 값과 똑같아야 하는 이름(예: "알코올"이 세테아릴알코올에 걸리지 않게)
 * @param contains 이 값이 들어 있으면 되는 이름
 */
record IngredientKeyword(Set<String> exact, Set<String> contains) {

	static String squash(String name) {
		return IngredientNameNormalizer.normalize(name).value().replace(" ", "").replace(".", "");
	}

	boolean matches(String name) {
		if (name == null || name.isBlank()) {
			return false;
		}
		String key = squash(name);
		return exact.contains(key) || contains.stream().anyMatch(key::contains);
	}

	boolean matchesAny(Collection<String> names) {
		return names.stream().anyMatch(this::matches);
	}
}
