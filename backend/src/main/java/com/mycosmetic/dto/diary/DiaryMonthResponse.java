package com.mycosmetic.dto.diary;

import java.util.List;

/**
 * GET /api/diary?month=YYYY-MM
 *
 * @param month   YYYY-MM
 * @param entries 그 달의 기록, 날짜순
 */
public record DiaryMonthResponse(String month, List<DiaryEntryResponse> entries) {
}
