package com.mycosmetic.controller;

import java.util.List;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mycosmetic.dto.product.NameCheckResponse;
import com.mycosmetic.service.product.review.ProductNameCheckService;

/** 새 제품 이름 점검·브랜드 목록 🌐(로그인 필요, 2026-10-26 제품명 예외처리). 저장하지 않는다 */
@RestController
@RequestMapping("/api/products")
public class ProductNameCheckController {

	private final ProductNameCheckService nameCheckService;

	public ProductNameCheckController(ProductNameCheckService nameCheckService) {
		this.nameCheckService = nameCheckService;
	}

	/** 저장 전에 이름 정리 제안·브랜드 표기 제안·같거나 비슷한 제품 */
	@GetMapping("/name-check")
	NameCheckResponse check(@RequestParam("brand") String brand, @RequestParam("name") String name,
			@RequestParam(name = "volume", required = false) String volume) {
		return nameCheckService.check(brand, name, volume);
	}

	/** 등록된 브랜드 표기(제품이 많은 순). 브랜드 입력 자동완성 */
	@GetMapping("/brands")
	List<String> brands() {
		return nameCheckService.brands();
	}
}
