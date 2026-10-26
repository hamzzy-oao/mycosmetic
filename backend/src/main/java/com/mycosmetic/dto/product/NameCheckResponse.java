package com.mycosmetic.dto.product;

import java.util.List;

/**
 * GET /api/products/name-check: 새 제품을 저장하기 전에 이름을 점검한 결과(2026-10-26).
 *
 * @param name             올리브영식 표기 규칙으로 정리한 제품명. 고칠 게 없으면 입력 그대로
 * @param volume           이름에서 찾은 용량(입력한 용량이 없을 때만). 없으면 null
 * @param reasons          정리한 이유(사람이 읽는 글). 비었으면 이름 규칙에 맞다
 * @param brandKnown       등록된 제품에 같은 브랜드가 있는지(공백·대소문자 무시)
 * @param brandSuggestion  등록된 브랜드 표기로 바꾸자는 제안(표기가 다르거나 비슷한 브랜드가 있을 때). 없으면 null
 * @param similar          같은 브랜드에서 이름이 같거나 비슷한 제품(비슷한 순, 최대 5개)
 */
public record NameCheckResponse(String name, String volume, List<String> reasons, boolean brandKnown,
		String brandSuggestion, List<SimilarProduct> similar) {

	/**
	 * @param similarity 0~100. 100이면 표기 규칙을 적용하고 리필·공백을 뺀 이름이 같다(sameName)
	 */
	public record SimilarProduct(ProductSummaryResponse product, int similarity, boolean sameName) {
	}

	/** 사용자에게 한 번 보여 줄 게 있는지 */
	public boolean needsAttention() {
		return !reasons.isEmpty() || brandSuggestion != null || !similar.isEmpty();
	}
}
