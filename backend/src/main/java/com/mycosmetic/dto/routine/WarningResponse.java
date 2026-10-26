package com.mycosmetic.dto.routine;

import java.util.List;

import com.mycosmetic.service.routine.RoutineWarning;
import com.mycosmetic.service.routine.WarningLevel;

/** 루틴 경고. items는 관련된 내 화장대 항목 ID */
public record WarningResponse(WarningLevel level, String code, String message, List<Long> items) {

	public static WarningResponse of(RoutineWarning warning) {
		return new WarningResponse(warning.level(), warning.code(), warning.message(), warning.items());
	}
}
