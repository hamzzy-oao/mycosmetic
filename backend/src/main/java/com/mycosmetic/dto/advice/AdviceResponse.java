package com.mycosmetic.dto.advice;

import java.util.List;

import com.mycosmetic.dto.product.ProductSummaryResponse;
import com.mycosmetic.service.advice.AdviceVerdict;

/**
 * "이거 사도 돼?" 판단 결과(no-ai-features 설계 F). 참고용이며 의학적 판단이 아니다.
 *
 * @param reasons         WARNING → CAUTION → POSITIVE → INFO 순
 * @param matchedCount    성분 사전에 매칭된 성분 수
 * @param unmatchedCount  매칭되지 않은 성분 수(원문으로 비교)
 * @param ingredients     판단한 성분 이름(표준명, 없으면 원문), 전성분 순서
 * @param product         제품으로 판단했을 때 그 제품(representativeListId로 "내 화장대에 담기"). 사진·붙여넣기면 null
 * @param alreadyOnShelf  같은 제품이 이미 내 화장대에 있는지
 * @param relatedItems    근거에 나온 내 화장대 항목
 */
public record AdviceResponse(AdviceVerdict verdict, List<AdviceReasonResponse> reasons, int matchedCount,
		int unmatchedCount, List<String> ingredients, ProductSummaryResponse product, boolean alreadyOnShelf,
		List<RelatedShelfItem> relatedItems) {
}
