package com.mycosmetic.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mycosmetic.dto.skin.SkinProfileRequest;
import com.mycosmetic.dto.skin.SkinProfileResponse;
import com.mycosmetic.security.CurrentUserId;
import com.mycosmetic.service.skin.SkinProfileService;

import jakarta.validation.Valid;

/** 내 피부 상태 🔒 */
@RestController
@RequestMapping("/api/skin-profile")
public class SkinProfileController {

	private final SkinProfileService skinProfileService;

	public SkinProfileController(SkinProfileService skinProfileService) {
		this.skinProfileService = skinProfileService;
	}

	@GetMapping
	SkinProfileResponse get(@CurrentUserId Long userId) {
		return skinProfileService.get(userId);
	}

	@PutMapping
	SkinProfileResponse put(@CurrentUserId Long userId, @Valid @RequestBody SkinProfileRequest request) {
		return skinProfileService.put(userId, request.skinTypes(), request.personalColor());
	}
}
