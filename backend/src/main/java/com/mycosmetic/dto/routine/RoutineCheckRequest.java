package com.mycosmetic.dto.routine;

import java.util.List;

import com.mycosmetic.entity.RoutineSlot;

import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * PUT /api/routines/today/checks: 오늘 이 시간대에 쓴 제품 체크(checked=true) 또는 해제(false). 날짜는 서버가 정한다.
 *
 * @param shelfItemIds 내 화장대 항목만(남의 항목은 404). "모두 했어요"처럼 여러 개를 한 번에 보낼 수 있다
 */
public record RoutineCheckRequest(@NotNull RoutineSlot slot, @NotEmpty @Size(max = 60) List<@NotNull Long> shelfItemIds,
		@NotNull Boolean checked) {
}
