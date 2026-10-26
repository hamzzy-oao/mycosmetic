package com.mycosmetic.dto.compare;

import java.util.List;

import com.mycosmetic.dto.product.ProductSummaryResponse;

/**
 * 두 제품의 성분 비교(no-ai-features 설계 B). 이름은 표준명(매칭 안 됐으면 원문)이다.
 *
 * @param common  두 버전에 모두 있는 성분(A의 순서)
 * @param onlyA   A에만 있는 성분(A의 순서)
 * @param onlyB   B에만 있는 성분(B의 순서)
 * @param groupsA A 성분이 속한 성분 그룹 코드(RETINOID, AHA, BHA, VITAMIN_C, BENZOYL_PEROXIDE, NIACINAMIDE)
 */
public record CompareResponse(Side a, Side b, List<String> common, List<String> onlyA, List<String> onlyB,
		List<String> groupsA, List<String> groupsB) {

	/**
	 * 비교한 한쪽.
	 *
	 * @param listId 비교에 쓴 성분 버전(화장대 항목이면 내가 담은 버전, 제품이면 대표 버전)
	 */
	public record Side(ProductSummaryResponse product, Long listId) {
	}
}
