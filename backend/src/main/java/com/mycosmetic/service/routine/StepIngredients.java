package com.mycosmetic.service.routine;

import java.util.Set;

import com.mycosmetic.entity.StepType;

/**
 * 충돌 판정에 쓰는 루틴 단계 정보.
 *
 * @param groups 이 항목의 성분 버전(내가 담은 버전)이 속한 성분 그룹
 */
public record StepIngredients(Long shelfItemId, StepType stepType, Set<IngredientGroup> groups) {

	public StepIngredients {
		groups = Set.copyOf(groups);
	}
}
