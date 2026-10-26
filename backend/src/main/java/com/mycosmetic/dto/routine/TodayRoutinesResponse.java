package com.mycosmetic.dto.routine;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.List;

/**
 * GET /api/routines/today: 오늘(Asia/Seoul) 요일에 하는 루틴과 오늘 사용 체크.
 *
 * @param checkedMorning 오늘 아침에 썼다고 체크한 화장대 항목 ID
 * @param checkedEvening 오늘 저녁에 썼다고 체크한 화장대 항목 ID
 * @param warnings       오늘 루틴을 아침끼리·저녁끼리 묶어 본 성분 충돌 경고(같은 날 아침·저녁 조합 포함)
 */
public record TodayRoutinesResponse(LocalDate date, DayOfWeek dayOfWeek, List<RoutineResponse> morning,
		List<RoutineResponse> evening, List<Long> checkedMorning, List<Long> checkedEvening,
		List<WarningResponse> warnings) {
}
