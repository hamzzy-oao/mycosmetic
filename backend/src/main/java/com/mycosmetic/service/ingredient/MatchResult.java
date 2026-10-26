package com.mycosmetic.service.ingredient;

import java.util.List;

import com.mycosmetic.entity.MatchType;

/**
 * 성분 하나의 사전 매칭 결과. 추출 job의 result_json items와 화면이 이 모양을 그대로 쓴다.
 *
 * @param position     전성분 표기 순서(1부터)
 * @param rawText      AI가 읽은 이름 그대로
 * @param type         EXACT / ALIAS / FUZZY / UNMATCHED
 * @param ingredientId EXACT·ALIAS일 때 성분 ID, 그 외 null
 * @param standardName EXACT·ALIAS일 때 표준명, 그 외 null
 * @param candidates   FUZZY일 때 후보(유사도 높은 순, 최대 3개), 그 외 빈 목록
 */
public record MatchResult(int position, String rawText, MatchType type, Long ingredientId, String standardName,
		List<Candidate> candidates) {

	public MatchResult {
		candidates = candidates == null ? List.of() : List.copyOf(candidates);
	}

	/** FUZZY 후보. similarity는 0.8 이상 1.0 이하 */
	public record Candidate(Long ingredientId, String standardName, double similarity) {
	}
}
