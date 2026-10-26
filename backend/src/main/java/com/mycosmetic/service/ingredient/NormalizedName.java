package com.mycosmetic.service.ingredient;

import java.util.List;

/**
 * 정규화한 성분 이름.
 *
 * @param value          매칭에 쓰는 괄호 밖 이름
 * @param parentheticals 떼어 낸 괄호 안 표기(예: "CI 77891"). 보조 정보
 */
public record NormalizedName(String value, List<String> parentheticals) {

	public NormalizedName {
		parentheticals = List.copyOf(parentheticals);
	}
}
