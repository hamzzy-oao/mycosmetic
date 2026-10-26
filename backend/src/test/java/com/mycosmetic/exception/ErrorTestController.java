package com.mycosmetic.exception;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;

/**
 * GlobalExceptionHandlerTest 전용 컨트롤러. 각 경로가 특정 예외를 일으킨다.
 */
@RestController
@RequestMapping("/api/test-errors")
class ErrorTestController {

	record NameRequest(@NotBlank String name) {
	}

	@GetMapping("/business")
	void business() {
		throw new BusinessException(ErrorCode.NOT_FOUND);
	}

	@PostMapping("/valid")
	String valid(@Valid @RequestBody NameRequest request) {
		return request.name();
	}

	@GetMapping("/unexpected")
	void unexpected() {
		throw new IllegalStateException("내부 비밀 정보 SELECT * FROM users");
	}

	@GetMapping("/too-large")
	void tooLarge() {
		throw new MaxUploadSizeExceededException(5 * 1024 * 1024);
	}
}
