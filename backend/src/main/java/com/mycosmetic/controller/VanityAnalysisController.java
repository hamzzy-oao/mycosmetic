package com.mycosmetic.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mycosmetic.dto.vanity.VanityAnalysisResponse;
import com.mycosmetic.security.CurrentUserId;
import com.mycosmetic.service.vanity.VanityAnalysisService;

/** 화장대 분석 🔒(퍼스널 탭 설계 3절). 읽기 전용 */
@RestController
@RequestMapping("/api/vanity")
public class VanityAnalysisController {

	private final VanityAnalysisService vanityAnalysisService;

	public VanityAnalysisController(VanityAnalysisService vanityAnalysisService) {
		this.vanityAnalysisService = vanityAnalysisService;
	}

	@GetMapping("/analysis")
	VanityAnalysisResponse analysis(@CurrentUserId Long userId) {
		return vanityAnalysisService.analyze(userId);
	}
}
