package com.mycosmetic.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mycosmetic.dto.compare.CompareResponse;
import com.mycosmetic.security.CurrentUserId;
import com.mycosmetic.service.compare.CompareService;

/** 제품 비교 🔒 (no-ai-features 설계 B) */
@RestController
public class CompareController {

	private final CompareService compareService;

	public CompareController(CompareService compareService) {
		this.compareService = compareService;
	}

	/** 각 쪽은 화장대 항목(a, b) 또는 제품의 대표 버전(aProduct, bProduct) 중 하나 */
	@GetMapping("/api/compare")
	CompareResponse compare(@CurrentUserId Long userId, @RequestParam(name = "a", required = false) Long a,
			@RequestParam(name = "aProduct", required = false) Long aProduct, @RequestParam(name = "b", required = false) Long b,
			@RequestParam(name = "bProduct", required = false) Long bProduct) {
		return compareService.compare(userId, a, aProduct, b, bProduct);
	}
}
