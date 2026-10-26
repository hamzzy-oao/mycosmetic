package com.mycosmetic.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mycosmetic.dto.skin.SkinAnalysisHistoryResponse;
import com.mycosmetic.dto.skin.SkinAnalysisRequest;
import com.mycosmetic.dto.skin.SkinAnalysisResponse;
import com.mycosmetic.dto.skin.SkinQuestionsResponse;
import com.mycosmetic.security.CurrentUserId;
import com.mycosmetic.service.skin.SkinAnalysisService;

/** 피부 분석(퍼스널 탭 설계 1절). 문항은 공개, 분석·이력은 🔒 */
@RestController
@RequestMapping("/api/skin-analysis")
public class SkinAnalysisController {

	private final SkinAnalysisService skinAnalysisService;

	public SkinAnalysisController(SkinAnalysisService skinAnalysisService) {
		this.skinAnalysisService = skinAnalysisService;
	}

	@GetMapping("/questions")
	SkinQuestionsResponse questions() {
		return skinAnalysisService.questions();
	}

	@PostMapping
	SkinAnalysisResponse analyze(@CurrentUserId Long userId, @RequestBody SkinAnalysisRequest request) {
		return skinAnalysisService.analyze(userId, request);
	}

	@GetMapping("/history")
	SkinAnalysisHistoryResponse history(@CurrentUserId Long userId) {
		return skinAnalysisService.history(userId);
	}
}
