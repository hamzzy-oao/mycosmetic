package com.mycosmetic.dto.notification;

import java.util.List;

/** GET /api/notifications. 단계(WARNING → CAUTION → INFO) 순 */
public record NotificationsResponse(List<NotificationItem> items) {
}
