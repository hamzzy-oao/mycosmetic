package com.mycosmetic.service.routine;

import java.util.List;

/**
 * 루틴 경고 하나.
 *
 * @param items 관련된 내 화장대 항목 ID
 */
public record RoutineWarning(WarningLevel level, String code, String message, List<Long> items) {

	public RoutineWarning {
		items = List.copyOf(items);
	}
}
