package com.mycosmetic.dto.extraction;

import com.mycosmetic.entity.JobStatus;

import tools.jackson.databind.JsonNode;

/**
 * GET /api/extractions/{jobId} 응답.
 *
 * @param errorCode FAILED일 때 AI_TIMEOUT, AI_UNAVAILABLE, AI_INVALID_RESPONSE, AI_REFUSED, SERVER_RESTARTED 등. 그 외 null
 * @param result    DONE일 때 result_json(ingredientSectionFound, unreadableParts, warning, items).
 *                  DONE인데 null이면 이미 확인(저장)된 job
 * @param confirmed 이미 확인(저장)한 job이면 true. FAILED job을 직접 붙여넣어 저장한 경우도 true라
 *                  화면이 확인 화면을 다시 열지 않는다
 */
public record ExtractionJobResponse(Long jobId, JobStatus status, String errorCode, JsonNode result,
		boolean confirmed) {
}
