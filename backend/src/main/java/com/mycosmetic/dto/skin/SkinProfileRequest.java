package com.mycosmetic.dto.skin;

import java.util.Set;

import com.mycosmetic.entity.PersonalColor;
import com.mycosmetic.entity.SkinType;

import jakarta.validation.constraints.NotNull;

/**
 * PUT /api/skin-profile. 보낸 값으로 덮어쓴다(빈 목록이면 모두 지운다).
 *
 * @param personalColor 없거나 null이면 퍼스널컬러를 지운다(선택 안 함). PUT이라 화면은 항상 현재 값을 함께 보낸다
 */
public record SkinProfileRequest(@NotNull Set<@NotNull SkinType> skinTypes, PersonalColor personalColor) {

	/** 피부 상태만 보내는 요청(퍼스널컬러 없음) */
	public SkinProfileRequest(Set<SkinType> skinTypes) {
		this(skinTypes, null);
	}
}
