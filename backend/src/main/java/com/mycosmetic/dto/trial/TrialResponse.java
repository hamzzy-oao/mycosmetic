package com.mycosmetic.dto.trial;

import java.time.LocalDate;
import java.util.List;

import com.mycosmetic.dto.product.ProductSummaryResponse;
import com.mycosmetic.entity.Verdict;

/**
 * 새 제품 테스트 하나.
 *
 * @param finishedOn 평가를 마친 날(Asia/Seoul). 아직이면 null. 기간 전에 끝냈으면 이 날까지가 테스트 기간이다
 * @param day        오늘이 몇째 날인지(1부터, totalDays를 넘지 않는다)
 * @param totalDays  테스트 기간(일)
 * @param verdict    화장대 항목의 지금 평가
 * @param days       시작일부터 totalDays일. 날마다 그날 일기의 기분(없으면 null)
 */
public record TrialResponse(Long id, Long shelfItemId, ProductSummaryResponse product, LocalDate startedOn,
		LocalDate endsOn, LocalDate finishedOn, int day, int totalDays, TrialStatus status, Verdict verdict,
		TrialSummary summary, List<TrialDay> days) {
}
