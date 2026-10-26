package com.mycosmetic.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.mycosmetic.dto.notification.NotificationsResponse;
import com.mycosmetic.security.CurrentUserId;
import com.mycosmetic.service.notification.NotificationService;

/** 알림 🔒 (설계 2026-10-24 C). 저장하지 않고 지금 상태로 계산한다 */
@RestController
@RequestMapping("/api/notifications")
public class NotificationController {

	private final NotificationService notificationService;

	public NotificationController(NotificationService notificationService) {
		this.notificationService = notificationService;
	}

	@GetMapping
	NotificationsResponse list(@CurrentUserId Long userId) {
		return notificationService.list(userId);
	}
}
