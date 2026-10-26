package com.mycosmetic.dto.notification;

import com.mycosmetic.service.routine.WarningLevel;

/**
 * 알림 하나.
 *
 * @param link 누르면 갈 화면 경로(예: /shelf)
 */
public record NotificationItem(NotificationType type, WarningLevel level, String title, String message,
		String link) {
}
