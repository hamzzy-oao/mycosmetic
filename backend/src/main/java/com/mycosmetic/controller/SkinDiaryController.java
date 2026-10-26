package com.mycosmetic.controller;

import java.time.LocalDate;

import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mycosmetic.dto.diary.DiaryEntryRequest;
import com.mycosmetic.dto.diary.DiaryEntryResponse;
import com.mycosmetic.dto.diary.DiaryMonthResponse;
import com.mycosmetic.security.CurrentUserId;
import com.mycosmetic.service.diary.SkinDiaryService;

import jakarta.validation.Valid;

/** 피부 일기 🔒 (설계 2026-10-24 A) */
@RestController
@RequestMapping("/api/diary")
public class SkinDiaryController {

	private final SkinDiaryService diaryService;

	public SkinDiaryController(SkinDiaryService diaryService) {
		this.diaryService = diaryService;
	}

	/** month=YYYY-MM, 없으면 이번 달 */
	@GetMapping
	DiaryMonthResponse month(@CurrentUserId Long userId,
			@RequestParam(name = "month", required = false) String month) {
		return diaryService.month(userId, month);
	}

	@PutMapping("/{date}")
	DiaryEntryResponse put(@CurrentUserId Long userId,
			@PathVariable("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date,
			@Valid @RequestBody DiaryEntryRequest request) {
		return diaryService.put(userId, date, request);
	}

	@DeleteMapping("/{date}")
	ResponseEntity<Void> delete(@CurrentUserId Long userId,
			@PathVariable("date") @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate date) {
		diaryService.delete(userId, date);
		return ResponseEntity.noContent().build();
	}
}
