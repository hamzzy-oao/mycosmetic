package com.mycosmetic.dto.skin;

import java.time.Instant;
import java.util.List;

import com.mycosmetic.entity.SkinConcern;
import com.mycosmetic.entity.SkinType;

/**
 * 피부 분석 결과.
 *
 * @param tags                 SENSITIVE·ACNE_PRONE(enum 선언 순서)
 * @param reasons              판정 근거 문장
 * @param suggestSensitive     안맞아요 기록이 많아 민감성 추가를 제안할지(동의할 때만 화면이 저장)
 * @param candidateIngredients 안맞아요 제품 2개 이상에 공통인 성분("확인해 볼 후보 성분", 판정에는 쓰지 않음)
 */
public record SkinAnalysisResponse(SkinType baseType, List<SkinType> tags, List<SkinConcern> concerns,
		List<String> reasons, boolean suggestSensitive, List<String> candidateIngredients, String ruleVersion,
		Instant createdAt) {
}
