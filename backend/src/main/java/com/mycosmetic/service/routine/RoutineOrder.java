package com.mycosmetic.service.routine;

import java.util.Comparator;
import java.util.List;

/** 권장 순서: 단계 종류의 선언 순서(StepType), 같은 종류끼리는 지금 순서를 유지한다 */
public final class RoutineOrder {

	private RoutineOrder() {
	}

	public static List<Long> suggest(List<StepIngredients> steps) {
		return steps.stream()
				.sorted(Comparator.comparingInt(s -> s.stepType().ordinal()))
				.map(StepIngredients::shelfItemId)
				.toList();
	}
}
