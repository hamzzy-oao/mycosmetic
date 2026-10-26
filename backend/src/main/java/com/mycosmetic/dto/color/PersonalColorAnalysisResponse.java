package com.mycosmetic.dto.color;

import java.time.Instant;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mycosmetic.dto.color.EstimateResponse.LabValue;
import com.mycosmetic.entity.ColorMethod;
import com.mycosmetic.entity.PersonalColor;

/**
 * 저장한 퍼스널컬러 결과. MANUAL이면 색 관련 값(lab, candidates, confidence, agreed)이 없다
 *
 * @param agreed 카메라 1순위 후보와 사용자가 고른 것이 같은가
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record PersonalColorAnalysisResponse(Long id, ColorMethod method, PersonalColor chosen,
		List<PersonalColor> candidates, Double confidence, Boolean agreed, LabValue lab, String ruleVersion,
		Instant createdAt) {
}
