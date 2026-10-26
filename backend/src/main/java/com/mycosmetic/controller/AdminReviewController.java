package com.mycosmetic.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mycosmetic.dto.review.DismissReviewRequest;
import com.mycosmetic.dto.review.ReviewResponse;
import com.mycosmetic.security.CurrentUserId;
import com.mycosmetic.service.ingredient.IngredientListRematchService.RematchReport;
import com.mycosmetic.service.product.review.ProductReviewService;

import jakarta.validation.Valid;

/** 관리자 점검함(2026-10-24). 관리자만, 아니면 403. 제품명·브랜드를 고치는 건 PUT /api/admin/products/{id}를 쓴다 */
@RestController
@RequestMapping("/api/admin/review")
public class AdminReviewController {

	private final ProductReviewService reviewService;

	public AdminReviewController(ProductReviewService reviewService) {
		this.reviewService = reviewService;
	}

	@GetMapping
	ReviewResponse review(@CurrentUserId Long userId) {
		return reviewService.review(userId);
	}

	/** "괜찮음": 이 제품의 이 문제를 지금 값(signature) 그대로면 다시 보여주지 않는다 */
	@PostMapping("/dismissals")
	ResponseEntity<Void> dismiss(@CurrentUserId Long userId, @Valid @RequestBody DismissReviewRequest request) {
		reviewService.dismiss(userId, request.productId(), request.type(), request.signature());
		return ResponseEntity.noContent().build();
	}

	/** 사용자가 등록한 제품 확인 완료 */
	@PostMapping("/products/{id}/verify")
	ResponseEntity<Void> verify(@CurrentUserId Long userId, @PathVariable("id") Long id) {
		reviewService.verify(userId, id);
		return ResponseEntity.noContent().build();
	}

	/** 모든 성분 버전의 띄어쓰기 고치기. {lists, items, skipped} */
	@PostMapping("/fix-spacing")
	RematchReport fixSpacing(@CurrentUserId Long userId) {
		return reviewService.fixSpacing(userId);
	}
}
