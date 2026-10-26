package com.mycosmetic.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mycosmetic.dto.routine.RoutineCheckRequest;
import com.mycosmetic.dto.routine.RoutineCheckResponse;
import com.mycosmetic.dto.routine.RoutineRequest;
import com.mycosmetic.dto.routine.RoutineResponse;
import com.mycosmetic.dto.routine.RoutineUsageResponse;
import com.mycosmetic.dto.routine.TodayRoutinesResponse;
import com.mycosmetic.security.CurrentUserId;
import com.mycosmetic.service.routine.RoutineService;

import jakarta.validation.Valid;

/** 루틴 플래너 🔒 */
@RestController
@RequestMapping("/api/routines")
public class RoutineController {

	private final RoutineService routineService;

	public RoutineController(RoutineService routineService) {
		this.routineService = routineService;
	}

	@GetMapping
	List<RoutineResponse> list(@CurrentUserId Long userId) {
		return routineService.list(userId);
	}

	@PostMapping
	ResponseEntity<RoutineResponse> create(@CurrentUserId Long userId, @Valid @RequestBody RoutineRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(routineService.create(userId, request));
	}

	@GetMapping("/today")
	TodayRoutinesResponse today(@CurrentUserId Long userId) {
		return routineService.today(userId);
	}

	/** 오늘 사용 체크·해제. 날짜는 서버(Asia/Seoul)가 정한다 */
	@PutMapping("/today/checks")
	TodayRoutinesResponse checkToday(@CurrentUserId Long userId, @Valid @RequestBody RoutineCheckRequest request) {
		return routineService.checkToday(userId, request);
	}

	/** 최근 4주 제품별 사용 일수 */
	@GetMapping("/usage")
	RoutineUsageResponse usage(@CurrentUserId Long userId) {
		return routineService.usage(userId);
	}

	/** 화장대 진단 초안을 루틴으로 저장. 전에 진단에서 저장한 루틴은 새 초안으로 바뀐다 */
	@PostMapping("/from-diagnosis")
	List<RoutineResponse> saveFromDiagnosis(@CurrentUserId Long userId) {
		return routineService.saveFromDiagnosis(userId);
	}

	/** 저장 전 미리 보기. routineId는 고치는 중인 루틴(새 루틴이면 없음) */
	@PostMapping("/check")
	RoutineCheckResponse checkDraft(@CurrentUserId Long userId, @RequestParam(name = "routineId", required = false) Long routineId,
			@Valid @RequestBody RoutineRequest request) {
		return routineService.checkDraft(userId, routineId, request);
	}

	@GetMapping("/{id}")
	RoutineResponse get(@CurrentUserId Long userId, @PathVariable("id") Long id) {
		return routineService.get(userId, id);
	}

	@PutMapping("/{id}")
	RoutineResponse update(@CurrentUserId Long userId, @PathVariable("id") Long id,
			@Valid @RequestBody RoutineRequest request) {
		return routineService.update(userId, id, request);
	}

	@DeleteMapping("/{id}")
	ResponseEntity<Void> delete(@CurrentUserId Long userId, @PathVariable("id") Long id) {
		routineService.delete(userId, id);
		return ResponseEntity.noContent().build();
	}

	@GetMapping("/{id}/check")
	RoutineCheckResponse check(@CurrentUserId Long userId, @PathVariable("id") Long id) {
		return routineService.check(userId, id);
	}
}
