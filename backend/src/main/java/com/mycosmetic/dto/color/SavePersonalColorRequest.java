package com.mycosmetic.dto.color;

import com.mycosmetic.entity.ColorMethod;
import com.mycosmetic.entity.PersonalColor;

import jakarta.validation.constraints.NotNull;

/**
 * POST /api/personal-color. 판정(후보·agreed)은 받지 않고 서버가 samples로 다시 계산한다.
 *
 * @param samples CAMERA면 필수, MANUAL이면 무시
 */
public record SavePersonalColorRequest(@NotNull ColorMethod method, ColorSamplesRequest samples,
		@NotNull PersonalColor chosen) {
}
