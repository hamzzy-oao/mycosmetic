package com.mycosmetic.service.notification;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mycosmetic.dto.notification.NotificationItem;
import com.mycosmetic.dto.notification.NotificationType;
import com.mycosmetic.dto.notification.NotificationsResponse;
import com.mycosmetic.dto.shelf.ExpiryStatus;
import com.mycosmetic.dto.shelf.ShelfItemResponse;
import com.mycosmetic.dto.trial.TrialResponse;
import com.mycosmetic.dto.trial.TrialStatus;
import com.mycosmetic.service.diary.SkinDiaryService;
import com.mycosmetic.service.routine.WarningLevel;
import com.mycosmetic.service.shelf.ShelfService;
import com.mycosmetic.service.trial.TrialService;

/**
 * 알림(설계 2026-10-24 C). 저장하지 않고 지금 상태로 계산한다. 🔒
 */
@Service
public class NotificationService {

	private final ShelfService shelfService;
	private final TrialService trialService;
	private final SkinDiaryService diaryService;

	public NotificationService(ShelfService shelfService, TrialService trialService, SkinDiaryService diaryService) {
		this.shelfService = shelfService;
		this.trialService = trialService;
		this.diaryService = diaryService;
	}

	/** 사용기한(다 쓴 제품 제외) → 평가 대기 테스트 → 오늘 일기. 단계 순으로, 같은 단계는 이 순서대로 */
	@Transactional(readOnly = true)
	public NotificationsResponse list(Long userId) {
		List<NotificationItem> items = new ArrayList<>();
		List<ShelfItemResponse> inUse = shelfService.list(userId).stream().filter(i -> i.endedOn() == null)
				.sorted(Comparator.comparing(ShelfItemResponse::id)).toList();
		for (ShelfItemResponse item : inUse) {
			if (item.expiryStatus() == ExpiryStatus.EXPIRED) {
				items.add(new NotificationItem(NotificationType.EXPIRED, WarningLevel.WARNING, "사용기한이 지났어요",
						name(item) + "의 사용기한이 " + item.expiresOn() + "에 끝났어요.", "/shelf"));
			} else if (item.expiryStatus() == ExpiryStatus.EXPIRING) {
				items.add(new NotificationItem(NotificationType.EXPIRING, WarningLevel.CAUTION, "사용기한이 얼마 안 남았어요",
						name(item) + "은(는) " + item.expiresOn() + "까지 쓸 수 있어요.", "/shelf"));
			}
		}
		List<TrialResponse> trials = trialService.list(userId);
		for (TrialResponse trial : trials) {
			if (trial.status() == TrialStatus.READY) {
				items.add(new NotificationItem(NotificationType.TRIAL_READY, WarningLevel.CAUTION, "테스트가 끝났어요",
						trial.product().brand() + " " + trial.product().name() + " 2주 테스트가 끝났어요. 잘 맞았는지 평가해 주세요.",
						"/trials"));
			}
		}
		if (!diaryService.wroteToday(userId)) {
			boolean testing = trials.stream().anyMatch(t -> t.status() == TrialStatus.ACTIVE);
			items.add(new NotificationItem(NotificationType.DIARY_TODAY, WarningLevel.INFO, "오늘 피부는 어땠나요?",
					testing ? "테스트 중인 제품이 있어요. 매일 기록하면 잘 맞는지 더 정확히 알 수 있어요."
							: "피부 일기를 남겨 보세요.",
					"/diary"));
		}
		items.sort(Comparator.comparing(NotificationItem::level));
		return new NotificationsResponse(List.copyOf(items));
	}

	private static String name(ShelfItemResponse item) {
		return item.product().brand() + " " + item.product().name();
	}
}
