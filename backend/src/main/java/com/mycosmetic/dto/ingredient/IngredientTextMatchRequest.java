package com.mycosmetic.dto.ingredient;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /api/ingredient-text/match 요청. 붙여넣은 전성분 글 */
public record IngredientTextMatchRequest(@NotBlank @Size(max = 30000) String text) {
}
