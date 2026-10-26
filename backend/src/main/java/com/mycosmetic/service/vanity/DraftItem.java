package com.mycosmetic.service.vanity;

import java.time.LocalDate;
import java.util.Set;

import com.mycosmetic.entity.StepType;
import com.mycosmetic.entity.Verdict;
import com.mycosmetic.service.routine.IngredientGroup;

/**
 * 루틴 초안에 넣을 수 있는 내 화장대 항목 하나(이미 BAD·만료·다 쓴 것은 걸러진 것).
 *
 * @param guessed 단계를 이름으로 추정했으면 true(사용자가 저장한 값이면 false)
 * @param groups  내가 담은 버전 성분의 그룹(레티노이드·AHA 등)
 */
public record DraftItem(Long shelfItemId, StepType stepType, boolean guessed, Verdict verdict, LocalDate startedOn,
		Set<IngredientGroup> groups) {

	public DraftItem {
		groups = Set.copyOf(groups);
	}
}
