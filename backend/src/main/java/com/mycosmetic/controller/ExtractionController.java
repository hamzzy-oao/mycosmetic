package com.mycosmetic.controller;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import com.mycosmetic.dto.extraction.ExtractionAcceptedResponse;
import com.mycosmetic.dto.extraction.ExtractionJobResponse;
import com.mycosmetic.security.CurrentUserId;
import com.mycosmetic.service.extraction.ExtractionService;

@RestController
@RequestMapping("/api/extractions")
public class ExtractionController {

	private final ExtractionService extractionService;

	public ExtractionController(ExtractionService extractionService) {
		this.extractionService = extractionService;
	}

	/** 전성분 사진 접수. 결과는 GET /api/extractions/{jobId}로 확인한다 */
	@PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
	ResponseEntity<ExtractionAcceptedResponse> accept(@CurrentUserId Long userId,
			@RequestParam("image") MultipartFile image) {
		Long jobId = extractionService.accept(userId, image);
		return ResponseEntity.accepted().body(new ExtractionAcceptedResponse(jobId));
	}

	/** 사진 없이 전성분 직접 붙여넣기 시작. AI를 부르지 않는다 → 201 { jobId } */
	@PostMapping("/manual")
	ResponseEntity<ExtractionAcceptedResponse> startManual(@CurrentUserId Long userId) {
		Long jobId = extractionService.startManual(userId);
		return ResponseEntity.status(HttpStatus.CREATED).body(new ExtractionAcceptedResponse(jobId));
	}

	@GetMapping("/{jobId}")
	ExtractionJobResponse get(@CurrentUserId Long userId, @PathVariable("jobId") Long jobId) {
		return extractionService.get(userId, jobId);
	}
}
