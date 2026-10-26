package com.mycosmetic.dto.routine;

import java.util.List;

import com.mycosmetic.entity.Category;
import com.mycosmetic.entity.Verdict;

/**
 * GET /api/routines/usage: 최근 기간 동안 오늘 사용 체크로 쌓인 제품별 사용 일수(v-8 분석의 근거).
 *
 * @param days        센 기간(오늘 포함 일수)
 * @param steadyDays  이 일수 이상 쓴 제품을 "꾸준히 쓴 제품"으로 본다
 * @param items       한 번이라도 체크한 제품(많이 쓴 순)
 */
public record RoutineUsageResponse(int days, int steadyDays, List<Item> items) {

	public RoutineUsageResponse {
		items = List.copyOf(items);
	}

	/**
	 * @param daysUsed 아침·저녁 어느 쪽이든 체크한 날짜 수
	 * @param steady   꾸준히 쓴 제품인지. 잘맞아요 평가와 함께면 더 믿을 수 있는 평가다
	 */
	public record Item(Long shelfItemId, String brand, String productName, Category category, Verdict verdict,
			int daysUsed, boolean steady) {
	}
}
