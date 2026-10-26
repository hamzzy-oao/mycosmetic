package com.mycosmetic.service.ingredient;

import org.apache.commons.text.similarity.LevenshteinDistance;

/**
 * 정규화된 편집 거리 유사도 (설계 4.1 5단계 FUZZY). 두 값 모두 {@link IngredientNameNormalizer}로 정규화된 이름을 넣는다.
 * {@code 1 - 편집거리 / 긴 쪽 길이}. 둘 다 비면 1.0.
 */
public final class NameSimilarity {

	private NameSimilarity() {
	}

	public static double of(String a, String b) {
		int max = Math.max(a.length(), b.length());
		if (max == 0) {
			return 1.0;
		}
		int distance = LevenshteinDistance.getDefaultInstance().apply(a, b);
		return 1.0 - (double) distance / max;
	}
}
