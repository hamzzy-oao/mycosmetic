package com.mycosmetic.dto.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * 사용자가 확인한 성분 한 줄. 사전에 매칭됐으면 ingredientId, 아니면 rawText만 있다.
 *
 * @param ingredientId 매칭된 성분 ID. 매칭 안 됐으면 null
 * @param rawText      사진에서 읽은(또는 사용자가 고친) 원문
 */
public record FinalItem(Long ingredientId, @NotBlank @Size(max = 500) String rawText) {
}
