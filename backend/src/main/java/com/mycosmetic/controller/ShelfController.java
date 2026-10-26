package com.mycosmetic.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mycosmetic.dto.shelf.AddShelfRequest;
import com.mycosmetic.dto.shelf.ShelfItemResponse;
import com.mycosmetic.dto.shelf.ShelfStatsResponse;
import com.mycosmetic.dto.shelf.UpdateShelfRequest;
import com.mycosmetic.security.CurrentUserId;
import com.mycosmetic.service.shelf.ShelfService;

import jakarta.validation.Valid;

/** 내 화장대 🔒 */
@RestController
@RequestMapping("/api/shelf")
public class ShelfController {

	private final ShelfService shelfService;

	public ShelfController(ShelfService shelfService) {
		this.shelfService = shelfService;
	}

	/**
	 * toneMatch=true면 내 톤에 맞는 색조(설계 E, ingredient보다 먼저 본다), ingredient가 있으면 성분으로 찾기(설계 C)
	 */
	@GetMapping
	List<ShelfItemResponse> list(@CurrentUserId Long userId, @RequestParam(name = "ingredient", required = false) String ingredient,
			@RequestParam(name = "toneMatch", defaultValue = "false") boolean toneMatch) {
		if (toneMatch) {
			return shelfService.toneMatch(userId);
		}
		if (ingredient != null) {
			return shelfService.searchByIngredient(userId, ingredient);
		}
		return shelfService.list(userId);
	}

	/** 화장대 통계(no-ai-features 설계 D) */
	@GetMapping("/stats")
	ShelfStatsResponse stats(@CurrentUserId Long userId) {
		return shelfService.stats(userId);
	}

	@PostMapping
	ResponseEntity<ShelfItemResponse> add(@CurrentUserId Long userId, @Valid @RequestBody AddShelfRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(shelfService.add(userId, request.ingredientListId()));
	}

	@PatchMapping("/{id}")
	ShelfItemResponse update(@CurrentUserId Long userId, @PathVariable("id") Long id,
			@Valid @RequestBody UpdateShelfRequest request) {
		return shelfService.update(userId, id, request);
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> remove(@CurrentUserId Long userId, @PathVariable("id") Long id) {
		shelfService.remove(userId, id);
		return ResponseEntity.noContent().build();
	}
}
