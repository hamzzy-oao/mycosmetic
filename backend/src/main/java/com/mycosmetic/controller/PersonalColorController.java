package com.mycosmetic.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mycosmetic.dto.color.ColorSamplesRequest;
import com.mycosmetic.dto.color.EstimateResponse;
import com.mycosmetic.dto.color.PalettesResponse;
import com.mycosmetic.dto.color.PersonalColorAnalysisResponse;
import com.mycosmetic.dto.color.SavePersonalColorRequest;
import com.mycosmetic.security.CurrentUserId;
import com.mycosmetic.service.color.PersonalColorService;

import jakarta.validation.Valid;

/** 퍼스널컬러(퍼스널 탭 설계 2절). 팔레트는 공개, 나머지는 🔒 */
@RestController
@RequestMapping("/api/personal-color")
public class PersonalColorController {

	private final PersonalColorService personalColorService;

	public PersonalColorController(PersonalColorService personalColorService) {
		this.personalColorService = personalColorService;
	}

	@GetMapping("/palettes")
	PalettesResponse palettes() {
		return personalColorService.palettes();
	}

	/** 로그인 사용자만 쓰게 해 계산을 남용하지 못하게 한다. 저장하지 않는다 */
	@PostMapping("/estimate")
	EstimateResponse estimate(@CurrentUserId Long userId, @RequestBody ColorSamplesRequest request) {
		return personalColorService.estimate(request);
	}

	@PostMapping
	PersonalColorAnalysisResponse save(@CurrentUserId Long userId, @Valid @RequestBody SavePersonalColorRequest request) {
		return personalColorService.save(userId, request);
	}

	/** 없으면 204 */
	@GetMapping("/latest")
	ResponseEntity<PersonalColorAnalysisResponse> latest(@CurrentUserId Long userId) {
		return personalColorService.latest(userId).map(ResponseEntity::ok)
				.orElseGet(() -> ResponseEntity.noContent().build());
	}
}
