package com.mycosmetic.dto.routine;

import java.util.List;

/**
 * GET /api/routines/{id}/check, POST /api/routines/check.
 *
 * @param suggestedOrder 권장 순서로 늘어놓은 화장대 항목 ID
 */
public record RoutineCheckResponse(List<WarningResponse> warnings, List<Long> suggestedOrder) {
}
