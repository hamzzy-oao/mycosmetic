package com.mycosmetic.dto.color;

import java.util.List;

import com.mycosmetic.entity.PersonalColor;

/**
 * 카메라 색값 판정 결과(저장하지 않음).
 *
 * @param candidates 1·2순위 계절
 * @param ordered    false면 신뢰도가 낮아 화면이 후보 순서 없이 드레이핑을 보여준다
 * @param quality    passed=false면 화면이 사유를 보여주고 다시 찍게 한다
 */
public record EstimateResponse(LabValue lab, double ita, double hue, double chroma, List<PersonalColor> candidates,
		double confidence, boolean ordered, QualityValue quality, String ruleVersion) {

	public record LabValue(double l, double a, double b) {
	}

	public record QualityValue(boolean passed, List<String> issues, double score) {
	}
}
