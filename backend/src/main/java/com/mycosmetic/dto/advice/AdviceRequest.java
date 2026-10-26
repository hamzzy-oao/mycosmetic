package com.mycosmetic.dto.advice;

import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;

/**
 * POST /api/advice 요청. 둘 중 정확히 하나만 보낸다(아니면 400 INVALID_REQUEST).
 *
 * @param productId       제품(대표 성분 버전으로 판단)
 * @param ingredientsText 붙여넣은 전성분. 쉼표·줄바꿈으로 나누며 3000자, 200개까지
 */
public record AdviceRequest(@Positive Long productId, @Size(max = 3000) String ingredientsText) {
}
