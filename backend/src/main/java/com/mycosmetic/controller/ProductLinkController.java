package com.mycosmetic.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mycosmetic.dto.product.ProductLinkPreviewRequest;
import com.mycosmetic.dto.product.ProductLinkPreviewResponse;
import com.mycosmetic.service.product.ProductLinkService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/product-links")
public class ProductLinkController {

	private final ProductLinkService productLinkService;

	public ProductLinkController(ProductLinkService productLinkService) {
		this.productLinkService = productLinkService;
	}

	/** 올리브영 상품 상세 링크에서 제품명·사진을 가져온다(전성분은 가져오지 않음) */
	@PostMapping("/preview")
	ProductLinkPreviewResponse preview(@Valid @RequestBody ProductLinkPreviewRequest request) {
		return productLinkService.preview(request.url());
	}
}
