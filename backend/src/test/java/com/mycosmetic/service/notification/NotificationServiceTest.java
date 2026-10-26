package com.mycosmetic.service.notification;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.mycosmetic.dto.diary.DiaryEntryRequest;
import com.mycosmetic.dto.notification.NotificationItem;
import com.mycosmetic.dto.notification.NotificationType;
import com.mycosmetic.dto.shelf.UpdateShelfRequest;
import com.mycosmetic.entity.DiaryMood;
import com.mycosmetic.entity.ProductTrial;
import com.mycosmetic.entity.User;
import com.mycosmetic.repository.ProductTrialRepository;
import com.mycosmetic.repository.ShelfItemRepository;
import com.mycosmetic.service.diary.SkinDiaryService;
import com.mycosmetic.service.routine.WarningLevel;
import com.mycosmetic.service.shelf.ShelfService;
import com.mycosmetic.service.trial.TrialService;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.ProductFixtures;
import com.mycosmetic.support.TestAuthHelper;

/** 알림 (설계 2026-10-24 C) */
class NotificationServiceTest extends IntegrationTestSupport {

	@Autowired
	NotificationService notificationService;

	@Autowired
	ShelfService shelfService;

	@Autowired
	TrialService trialService;

	@Autowired
	SkinDiaryService diaryService;

	@Autowired
	ProductTrialRepository trialRepository;

	@Autowired
	ShelfItemRepository shelfItemRepository;

	@Autowired
	ProductFixtures fixtures;

	@Autowired
	TestAuthHelper auth;

	private User newUser() {
		return auth.createUser(TestAuthHelper.uniqueEmail());
	}

	private Long shelfItem(User user) {
		return shelfService.add(user.getId(), fixtures.list(fixtures.product(), 1, "정제수").getId()).id();
	}

	@Test
	@DisplayName("지남(WARNING) → 임박·평가 대기(CAUTION) → 오늘 일기(INFO). 다 쓴 제품은 빼고, 테스트 중이면 일기 문구가 바뀐다")
	void list_allKinds() {
		User user = newUser();
		LocalDate today = diaryService.today();
		Long expiring = shelfItem(user);
		shelfService.update(user.getId(), expiring,
				new UpdateShelfRequest(null, null, null, null, today.minusMonths(1).plusDays(10), 1));
		Long expired = shelfItem(user);
		shelfService.update(user.getId(), expired,
				new UpdateShelfRequest(null, null, null, null, today.minusMonths(3), 1));
		Long finished = shelfItem(user);
		shelfService.update(user.getId(), finished,
				new UpdateShelfRequest(null, null, today.minusMonths(4), today.minusDays(1), today.minusMonths(3), 1));
		Long readyItem = shelfItem(user);
		trialRepository.saveAndFlush(ProductTrial.start(user.getId(), shelfItemRepository.findById(readyItem).orElseThrow(),
				today.minusDays(20), today.minusDays(7)));
		trialService.start(user.getId(), shelfItem(user));

		var items = notificationService.list(user.getId()).items();

		assertThat(items).extracting(NotificationItem::type).containsExactly(NotificationType.EXPIRED,
				NotificationType.EXPIRING, NotificationType.TRIAL_READY, NotificationType.DIARY_TODAY);
		assertThat(items).extracting(NotificationItem::level).containsExactly(WarningLevel.WARNING,
				WarningLevel.CAUTION, WarningLevel.CAUTION, WarningLevel.INFO);
		assertThat(items.get(2).link()).isEqualTo("/trials");
		assertThat(items.get(3).link()).isEqualTo("/diary");
		assertThat(items.get(3).message()).contains("테스트 중인 제품");
	}

	@Test
	@DisplayName("오늘 일기를 썼고 다른 일이 없으면 알림 없음")
	void list_empty() {
		User user = newUser();
		diaryService.put(user.getId(), diaryService.today(), new DiaryEntryRequest(DiaryMood.GOOD, Set.of(), null));

		assertThat(notificationService.list(user.getId()).items()).isEmpty();
	}
}
