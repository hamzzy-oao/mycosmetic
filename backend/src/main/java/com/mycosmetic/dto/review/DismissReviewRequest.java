package com.mycosmetic.dto.review;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/** POST /api/admin/review/dismissals. signature는 점검함이 준 값 그대로 */
public record DismissReviewRequest(@NotNull Long productId, @NotNull ReviewIssueType type,
		@NotBlank @Size(max = 300) String signature) {
}
