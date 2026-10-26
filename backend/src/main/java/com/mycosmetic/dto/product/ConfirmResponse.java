package com.mycosmetic.dto.product;

import com.mycosmetic.entity.ListStatus;

/**
 * 확인·저장 결과.
 *
 * @param productId        저장한(또는 찾은) 제품
 * @param ingredientListId 내가 확인한 성분 버전. 내 화장대 항목이 이 버전에 고정된다
 * @param status           확인을 더한 뒤의 버전 상태
 * @param shelfItemId      내 화장대 항목(이미 있었으면 그 항목)
 */
public record ConfirmResponse(Long productId, Long ingredientListId, ListStatus status, Long shelfItemId) {
}
