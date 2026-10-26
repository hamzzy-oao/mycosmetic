package com.mycosmetic.service.advice;

import java.util.List;
import java.util.Objects;

import com.mycosmetic.dto.product.FinalItem;
import com.mycosmetic.service.product.ListFingerprint;

/**
 * 판단에 쓰는 성분 하나.
 *
 * @param token 비교 키({@link ListFingerprint#token}: 매칭된 성분은 ID, 아니면 정규화한 원문)
 * @param name  화면 이름(표준명, 없으면 원문)
 * @param names 키워드 판정에 쓰는 이름들(원문과 표준명)
 */
public record AdviceIngredient(String token, String name, List<String> names) {

	public AdviceIngredient {
		names = names.stream().filter(Objects::nonNull).toList();
	}

	/** 사전 매칭 결과(성분 ID와 표준명, 없으면 null)와 원문으로 만든다 */
	public static AdviceIngredient of(Long ingredientId, String standardName, String rawText) {
		String token = ListFingerprint.token(new FinalItem(ingredientId, rawText));
		String name = standardName != null ? standardName : rawText;
		return new AdviceIngredient(token, name, standardName == null ? List.of(rawText) : List.of(rawText, standardName));
	}
}
