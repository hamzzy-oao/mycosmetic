package com.mycosmetic.dto.review;

import java.time.Instant;

/**
 * 중복 의심 묶음의 제품 하나. 어느 것을 남길지 고를 때 본다.
 *
 * @param shelfCount      이 제품을 담은 화장대 항목 수
 * @param ingredientLines 대표 성분 버전의 줄 수. 버전이 없으면 0
 */
public record ReviewMember(Long productId, String brand, String name, String volume, Instant createdAt,
		long shelfCount, int ingredientLines) {
}
