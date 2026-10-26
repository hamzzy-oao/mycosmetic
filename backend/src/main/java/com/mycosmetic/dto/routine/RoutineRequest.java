package com.mycosmetic.dto.routine;

import java.time.DayOfWeek;
import java.util.List;
import java.util.Set;

import com.mycosmetic.entity.RoutineSlot;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * POST /api/routines, PUT /api/routines/{id}(전체 교체), POST /api/routines/check(저장 전 미리 보기).
 *
 * @param days  요일 집합. null이나 빈 배열이면 매일
 * @param steps 바르는 순서대로. 같은 화장대 항목을 두 번 넣을 수 없다
 */
public record RoutineRequest(@NotBlank @Size(max = 50) String name, @NotNull RoutineSlot slot,
		Set<DayOfWeek> days, @NotNull @Size(max = 30) List<@Valid @NotNull StepRequest> steps) {
}
