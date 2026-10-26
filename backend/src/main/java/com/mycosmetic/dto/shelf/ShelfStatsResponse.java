package com.mycosmetic.dto.shelf;

import java.util.List;
import java.util.Map;

import com.mycosmetic.entity.Category;
import com.mycosmetic.entity.Verdict;

/**
 * 내 화장대 통계(no-ai-features 설계 D).
 *
 * @param inUse                사용 종료일이 없는 항목 수
 * @param finished             다 쓴 항목(사용 종료일이 있음) 수
 * @param byCategory           분류별 항목 수. 모든 분류가 0부터 들어 있다
 * @param byVerdict            평가별 항목 수. 모든 평가가 0부터 들어 있다
 * @param expiring             쓰는 중인 항목 중 30일 안에 사용기한이 끝나는 것(사용기한 가까운 순)
 * @param expired              쓰는 중인 항목 중 사용기한이 지난 것(사용기한 오래된 순)
 * @param avgUsageDaysFinished 다 쓴 항목 중 시작일이 있는 것의 평균 사용 일수(소수 첫째 자리). 없으면 null
 * @param ingredientInsights   맞음·안 맞음 평가로 가려낸 성분(v-8)
 */
public record ShelfStatsResponse(long total, long inUse, long finished, Map<Category, Long> byCategory,
		Map<Verdict, Long> byVerdict, List<ShelfItemResponse> expiring, List<ShelfItemResponse> expired,
		Double avgUsageDaysFinished, IngredientInsightsResponse ingredientInsights) {
}
