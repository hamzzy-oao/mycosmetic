package com.mycosmetic.controller;

import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mycosmetic.dto.user.MeResponse;
import com.mycosmetic.dto.user.UpdateNicknameRequest;
import com.mycosmetic.security.CurrentUserId;
import com.mycosmetic.security.TokenCookies;
import com.mycosmetic.service.user.UserService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/users")
public class UserController {

	private final UserService userService;
	private final TokenCookies tokenCookies;

	public UserController(UserService userService, TokenCookies tokenCookies) {
		this.userService = userService;
		this.tokenCookies = tokenCookies;
	}

	@GetMapping("/me")
	MeResponse me(@CurrentUserId Long userId) {
		return userService.me(userId);
	}

	/** 닉네임 변경 → 바뀐 내 정보 */
	@PatchMapping("/me")
	MeResponse changeNickname(@CurrentUserId Long userId, @Valid @RequestBody UpdateNicknameRequest request) {
		return userService.changeNickname(userId, request.nickname());
	}

	/** 탈퇴. 본인 데이터를 지우고 access·refresh 쿠키를 지운다 */
	@DeleteMapping("/me")
	ResponseEntity<Void> withdraw(@CurrentUserId Long userId) {
		userService.withdraw(userId);
		return ResponseEntity.noContent()
				.header(HttpHeaders.SET_COOKIE, tokenCookies.clearAccess().toString())
				.header(HttpHeaders.SET_COOKIE, tokenCookies.clearRefresh().toString())
				.build();
	}
}
