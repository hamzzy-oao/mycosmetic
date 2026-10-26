package com.mycosmetic.service.routine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mycosmetic.entity.StepType;

class RoutineOrderTest {

	private static StepIngredients step(long id, StepType type) {
		return new StepIngredients(id, type, Set.of());
	}

	@Test
	@DisplayName("단계 종류 선언 순서(클렌저→토너→…→크림→선크림→마스크→기타)로 늘어놓는다")
	void sortsByStepType() {
		List<StepIngredients> steps = List.of(step(1, StepType.SUNSCREEN), step(2, StepType.CREAM),
				step(3, StepType.ETC), step(4, StepType.CLEANSER), step(5, StepType.SERUM), step(6, StepType.TONER),
				step(7, StepType.MASK), step(8, StepType.EYE));

		assertThat(RoutineOrder.suggest(steps)).containsExactly(4L, 6L, 5L, 8L, 2L, 1L, 7L, 3L);
	}

	@Test
	@DisplayName("같은 종류끼리는 지금 순서를 유지한다")
	void stableWithinType() {
		List<StepIngredients> steps = List.of(step(9, StepType.SERUM), step(3, StepType.TONER),
				step(5, StepType.SERUM), step(1, StepType.SERUM));

		assertThat(RoutineOrder.suggest(steps)).containsExactly(3L, 9L, 5L, 1L);
	}

	@Test
	@DisplayName("단계가 없으면 빈 목록")
	void empty() {
		assertThat(RoutineOrder.suggest(List.of())).isEmpty();
	}
}
