package com.mycosmetic.dto.skin;

import java.time.Instant;
import java.util.List;

import com.mycosmetic.entity.SkinConcern;
import com.mycosmetic.entity.SkinType;

/**
 * 최근 피부 분석 10건(최신순).
 *
 * @param changes 가장 최근 결과가 바로 전 결과와 달라진 점(문장). 1건 이하면 빈 목록
 */
public record SkinAnalysisHistoryResponse(List<Item> items, List<String> changes) {

	public record Item(Long id, SkinType baseType, List<SkinType> tags, List<SkinConcern> concerns,
			String ruleVersion, Instant createdAt) {
	}
}
