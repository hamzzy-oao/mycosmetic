package com.mycosmetic.service.advice;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.mycosmetic.entity.StepType;
import com.mycosmetic.service.routine.IngredientGroup;
import com.mycosmetic.service.routine.RoutineConflictRules;
import com.mycosmetic.service.routine.RoutineConflictRules.OtherRoutine;
import com.mycosmetic.service.routine.RoutineWarning;
import com.mycosmetic.service.routine.StepIngredients;

/**
 * 루틴 충돌 미리보기(no-ai-features 설계 F-5). 대상 제품을 내 루틴마다 한 단계로 넣어 보고
 * 루틴 충돌 규칙({@link RoutineConflictRules})이 대상과 관련해 새로 내는 경고를 정보 근거로 바꾼다.
 * 같은 날 다른 루틴과의 비교는 하지 않는다(루틴 하나 안의 충돌과 아침 루틴 자외선 주의만).
 */
final class RoutinePreview {

	/** 넣어 보는 대상 제품의 가짜 화장대 항목 ID(실제 ID는 양수) */
	static final Long TARGET_ID = -1L;

	private static final Set<IngredientGroup> SUN_SENSITIVE = EnumSet.of(IngredientGroup.RETINOID,
			IngredientGroup.AHA, IngredientGroup.BHA);

	private RoutinePreview() {
	}

	static List<AdviceReason> check(List<AdviceIngredient> target, List<OtherRoutine> routines) {
		Set<IngredientGroup> targetGroups = IngredientGroup.detect(
				target.stream().flatMap(i -> i.names().stream()).toList());
		if (targetGroups.isEmpty()) {
			return List.of();
		}
		List<AdviceReason> reasons = new ArrayList<>();
		for (OtherRoutine routine : routines) {
			List<StepIngredients> steps = new ArrayList<>(routine.steps());
			steps.add(new StepIngredients(TARGET_ID, StepType.ETC, targetGroups));
			for (RoutineWarning warning : RoutineConflictRules.check(routine.slot(), routine.days(), steps, List.of())) {
				if (!warning.items().contains(TARGET_ID)) {
					continue;
				}
				List<Long> related = warning.items().stream().filter(id -> !TARGET_ID.equals(id)).toList();
				reasons.add(new AdviceReason(ReasonType.ROUTINE_CONFLICT, AdviceLevel.INFO,
						"'%s' 루틴에 넣으면: %s".formatted(routine.name(), warning.message()),
						namesIn(target, involvedGroups(warning.code())), related));
			}
		}
		return reasons;
	}

	/** 경고 코드(RETINOID_AHA, MORNING_SUN_SENSITIVE 등)에 나오는 성분 그룹 */
	static Set<IngredientGroup> involvedGroups(String code) {
		if ("MORNING_SUN_SENSITIVE".equals(code)) {
			return SUN_SENSITIVE;
		}
		Set<IngredientGroup> groups = EnumSet.noneOf(IngredientGroup.class);
		for (IngredientGroup group : IngredientGroup.values()) {
			if (code.contains(group.name())) {
				groups.add(group);
			}
		}
		return groups;
	}

	private static List<String> namesIn(List<AdviceIngredient> target, Set<IngredientGroup> groups) {
		Set<String> names = new LinkedHashSet<>();
		for (AdviceIngredient ingredient : target) {
			Set<IngredientGroup> own = IngredientGroup.detect(ingredient.names());
			if (own.stream().anyMatch(groups::contains)) {
				names.add(ingredient.name());
			}
		}
		return List.copyOf(names);
	}
}
