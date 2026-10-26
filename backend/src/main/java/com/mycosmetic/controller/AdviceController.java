package com.mycosmetic.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

import com.mycosmetic.dto.advice.AdviceRequest;
import com.mycosmetic.dto.advice.AdviceResponse;
import com.mycosmetic.security.CurrentUserId;
import com.mycosmetic.service.advice.AdviceService;

import jakarta.validation.Valid;

/** "이거 사도 돼?" 규칙 판단 🔒 (no-ai-features 설계 F). 아무것도 저장하지 않지만 본문을 받으므로 POST(CSRF 필요) */
@RestController
public class AdviceController {

	private final AdviceService adviceService;

	public AdviceController(AdviceService adviceService) {
		this.adviceService = adviceService;
	}

	/** body는 {productId} | {ingredientsText} 중 하나 */
	@PostMapping("/api/advice")
	AdviceResponse advise(@CurrentUserId Long userId, @Valid @RequestBody AdviceRequest request) {
		return adviceService.advise(userId, request);
	}
}
