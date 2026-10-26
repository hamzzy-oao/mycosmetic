package com.mycosmetic.service.skin;

import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import com.mycosmetic.entity.SkinConcern;
import com.mycosmetic.entity.SkinType;

/**
 * 피부 분석 판정 결과.
 *
 * @param tags             SENSITIVE·ACNE_PRONE 0~2개
 * @param reasons          판정 근거 문장(화면에 그대로 보여준다)
 * @param suggestSensitive 안맞아요 기록이 많아 민감성 추가를 제안할지
 */
public record SkinAnalysisResult(SkinType baseType, Set<SkinType> tags, Set<SkinConcern> concerns,
		List<String> reasons, boolean suggestSensitive) {

	public SkinAnalysisResult {
		// EnumSet이라 순회 순서가 enum 선언 순서다
		tags = Collections.unmodifiableSet(tags.isEmpty() ? EnumSet.noneOf(SkinType.class) : EnumSet.copyOf(tags));
		concerns = Collections.unmodifiableSet(
				concerns.isEmpty() ? EnumSet.noneOf(SkinConcern.class) : EnumSet.copyOf(concerns));
		reasons = List.copyOf(reasons);
	}

	/** 저장할 피부 타입 집합(기본 타입 + 태그) */
	public Set<SkinType> types() {
		EnumSet<SkinType> types = EnumSet.of(baseType);
		types.addAll(tags);
		return types;
	}
}
