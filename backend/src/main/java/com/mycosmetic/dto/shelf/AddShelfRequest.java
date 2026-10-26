package com.mycosmetic.dto.shelf;

import jakarta.validation.constraints.NotNull;

/** POST /api/shelf: 기존 제품의 성분 버전을 내 화장대에 담는다 */
public record AddShelfRequest(@NotNull Long ingredientListId) {
}
