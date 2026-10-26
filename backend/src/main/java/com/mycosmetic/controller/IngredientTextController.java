package com.mycosmetic.controller;

import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mycosmetic.dto.ingredient.IngredientTextMatchRequest;
import com.mycosmetic.dto.ingredient.IngredientTextMatchResponse;
import com.mycosmetic.service.ingredient.IngredientTextService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/ingredient-text")
public class IngredientTextController {

	private final IngredientTextService ingredientTextService;

	public IngredientTextController(IngredientTextService ingredientTextService) {
		this.ingredientTextService = ingredientTextService;
	}

	/** 붙여넣은 전성분을 구역별로 나눠 사전과 맞춘다. 저장하지 않는다 */
	@PostMapping("/match")
	IngredientTextMatchResponse match(@Valid @RequestBody IngredientTextMatchRequest request) {
		return new IngredientTextMatchResponse(ingredientTextService.matchSections(request.text()));
	}
}
