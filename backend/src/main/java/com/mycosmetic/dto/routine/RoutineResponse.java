package com.mycosmetic.dto.routine;

import java.time.DayOfWeek;
import java.time.Instant;
import java.util.List;
import java.util.Set;

import com.mycosmetic.entity.Routine;
import com.mycosmetic.entity.RoutineSlot;

/**
 * 내 루틴. days가 비면 매일.
 *
 * @param fromDiagnosis 화장대 진단의 "루틴으로 저장"으로 만든 루틴(다시 저장하면 새 초안으로 바뀐다)
 */
public record RoutineResponse(Long id, String name, RoutineSlot slot, Set<DayOfWeek> days,
		List<RoutineStepResponse> steps, Instant updatedAt, boolean fromDiagnosis) {

	public static RoutineResponse of(Routine routine) {
		return new RoutineResponse(routine.getId(), routine.getName(), routine.getSlot(), routine.getDays(),
				routine.getSteps().stream().map(RoutineStepResponse::of).toList(), routine.getUpdatedAt(),
				routine.isFromDiagnosis());
	}
}
