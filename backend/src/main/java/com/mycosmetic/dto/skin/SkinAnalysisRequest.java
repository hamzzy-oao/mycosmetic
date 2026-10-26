package com.mycosmetic.dto.skin;

import java.util.List;

import com.mycosmetic.entity.SkinConcern;
import com.mycosmetic.service.skin.MiddayFeel;
import com.mycosmetic.service.skin.SelfType;

/**
 * POST /api/skin-analysis(퍼스널 탭 설계 1절). 값 검사(Q1 없음, 잘 모르겠어요인데 Q1-1 없음, 고민 4개 이상·중복)는
 * 판정 규칙이 INVALID_SKIN_ANSWERS로 한다
 *
 * @param middayFeel selfType이 UNSURE일 때만 쓴다
 * @param concerns   0~3개
 */
public record SkinAnalysisRequest(SelfType selfType, MiddayFeel middayFeel, List<SkinConcern> concerns) {
}
