package com.mycosmetic.dto.product;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** POST /api/product-links/preview 요청. 올리브영 상품 상세 링크 */
public record ProductLinkPreviewRequest(@NotBlank @Size(max = 2000) String url) {
}
