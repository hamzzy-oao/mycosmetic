package com.mycosmetic.client.mfds;

import java.util.List;

/**
 * 식약처 원료성분 한 건.
 *
 * @param koreanName  표준명(INGR_KOR_NAME)
 * @param englishName 영문명(INGR_ENG_NAME), 없으면 null
 * @param casNo       CAS 번호(CAS_NO), 없으면 null
 * @param synonyms    이명(INGR_SYNONYM을 나눈 것)
 */
public record MfdsIngredientRow(String koreanName, String englishName, String casNo, List<String> synonyms) {

	public MfdsIngredientRow {
		synonyms = synonyms == null ? List.of() : List.copyOf(synonyms);
	}
}
