package com.mycosmetic.client.ai;

import java.util.List;

import com.fasterxml.jackson.annotation.JsonPropertyDescription;

/**
 * Claude 구조화 출력의 스키마. 필드 설명은 JSON 스키마의 description으로 모델에 전달된다.
 */
public record ExtractionResult(
		@JsonPropertyDescription("사진에 화장품 전성분 표기 영역이 있으면 true, 없으면 false")
		boolean ingredientSectionFound,
		@JsonPropertyDescription("전성분 표기에 적힌 순서대로의 성분 이름 목록. 성분 하나가 원소 하나이며, 사진에 보이는 글자 그대로 옮긴다")
		List<String> ingredients,
		@JsonPropertyDescription("읽을 수 없었던 부분의 위치 설명 목록 (예: 3번째 줄 끝부분). 없으면 빈 배열")
		List<String> unreadableParts) {

	public ExtractionResult {
		ingredients = ingredients == null ? List.of() : List.copyOf(ingredients);
		unreadableParts = unreadableParts == null ? List.of() : List.copyOf(unreadableParts);
	}
}
