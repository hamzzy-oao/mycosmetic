package com.mycosmetic.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mycosmetic.dto.trial.FinishTrialRequest;
import com.mycosmetic.dto.trial.StartTrialRequest;
import com.mycosmetic.dto.trial.TrialResponse;
import com.mycosmetic.security.CurrentUserId;
import com.mycosmetic.service.trial.TrialService;

import jakarta.validation.Valid;

/** 새 제품 2주 테스트 🔒 (설계 2026-10-24 B) */
@RestController
@RequestMapping("/api/trials")
public class TrialController {

	private final TrialService trialService;

	public TrialController(TrialService trialService) {
		this.trialService = trialService;
	}

	@GetMapping
	List<TrialResponse> list(@CurrentUserId Long userId) {
		return trialService.list(userId);
	}

	@PostMapping
	ResponseEntity<TrialResponse> start(@CurrentUserId Long userId, @Valid @RequestBody StartTrialRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(trialService.start(userId, request.shelfItemId()));
	}

	@PostMapping("/{id}/finish")
	TrialResponse finish(@CurrentUserId Long userId, @PathVariable("id") Long id,
			@Valid @RequestBody FinishTrialRequest request) {
		return trialService.finish(userId, id, request.verdict());
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> cancel(@CurrentUserId Long userId, @PathVariable("id") Long id) {
		trialService.cancel(userId, id);
		return ResponseEntity.noContent().build();
	}
}
