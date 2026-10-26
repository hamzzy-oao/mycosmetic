package com.mycosmetic.dto.review;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mycosmetic.entity.Category;

/**
 * 점검함 문제 하나.
 *
 * @param productId         문제가 있는 제품. 중복 의심은 묶음에서 가장 먼저 등록된 제품
 * @param reasons           사람이 읽는 이유
 * @param examples          예시(예: "글리세 린 → 글리세린"). 최대 3개
 * @param suggestion        제안대로 고칠 제품 정보. 사람이 직접 고쳐야 하면 null
 * @param members           중복 의심 묶음의 제품들(등록 순). 다른 종류에는 없다
 * @param signature         "문제없음"으로 숨길 때 보내는 값. 바뀌면 다시 뜬다(중복 의심은 묶음 제품 id들)
 */
public record ReviewIssueResponse(Long productId, String brand, String name, String volume, Category category,
		ReviewIssueType type, List<String> reasons, List<String> examples, ReviewSuggestion suggestion,
		@JsonInclude(JsonInclude.Include.NON_NULL) List<ReviewMember> members, String signature) {
}
