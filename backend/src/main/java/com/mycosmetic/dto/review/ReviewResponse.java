package com.mycosmetic.dto.review;

import java.util.List;
import java.util.Map;

/**
 * GET /api/admin/review
 *
 * @param checkedProducts 점검한 제품 수(보관한 제품 제외)
 * @param counts          종류별 문제 수. 모든 종류가 0부터 들어 있다
 * @param spacingLines    띄어쓰기를 고치면 사전에 맞는 성분 줄 수(모든 제품 합)
 * @param dismissed       "괜찮음"으로 넘겨서 숨긴 문제 수
 */
public record ReviewResponse(int checkedProducts, Map<ReviewIssueType, Long> counts, long spacingLines,
		long dismissed, List<ReviewIssueResponse> issues) {
}
