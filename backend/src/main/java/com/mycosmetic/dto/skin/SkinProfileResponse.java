package com.mycosmetic.dto.skin;

import java.util.List;

import com.mycosmetic.entity.PersonalColor;
import com.mycosmetic.entity.SkinConcern;
import com.mycosmetic.entity.SkinType;

/**
 * @param skinTypes     enum 선언 순서. 저장한 적이 없으면 빈 목록
 * @param personalColor 선택 안 했으면 null
 * @param concerns      피부 고민(enum 선언 순서, 퍼스널 탭 설계 1절)
 */
public record SkinProfileResponse(List<SkinType> skinTypes, PersonalColor personalColor, List<SkinConcern> concerns) {

	/** 고민 없이(빈 목록) */
	public SkinProfileResponse(List<SkinType> skinTypes, PersonalColor personalColor) {
		this(skinTypes, personalColor, List.of());
	}
}
