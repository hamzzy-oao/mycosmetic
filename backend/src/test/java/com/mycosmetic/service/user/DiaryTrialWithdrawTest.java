package com.mycosmetic.service.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.mycosmetic.dto.diary.DiaryEntryRequest;
import com.mycosmetic.dto.trial.TrialResponse;
import com.mycosmetic.entity.DiaryMood;
import com.mycosmetic.entity.User;
import com.mycosmetic.repository.ProductTrialRepository;
import com.mycosmetic.repository.SkinDiaryEntryRepository;
import com.mycosmetic.service.diary.SkinDiaryService;
import com.mycosmetic.service.shelf.ShelfService;
import com.mycosmetic.service.trial.TrialService;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.ProductFixtures;
import com.mycosmetic.support.TestAuthHelper;

/** 탈퇴하면 피부 일기와 새 제품 테스트도 지워진다(V14의 ON DELETE CASCADE) */
class DiaryTrialWithdrawTest extends IntegrationTestSupport {

	@Autowired
	UserService userService;

	@Autowired
	SkinDiaryService diaryService;

	@Autowired
	TrialService trialService;

	@Autowired
	ShelfService shelfService;

	@Autowired
	SkinDiaryEntryRepository diaryRepository;

	@Autowired
	ProductTrialRepository trialRepository;

	@Autowired
	ProductFixtures fixtures;

	@Autowired
	TestAuthHelper auth;

	@Test
	@DisplayName("탈퇴: 내 일기·테스트는 지워지고 다른 사람 것은 남는다")
	void withdraw_removesDiaryAndTrials() {
		User me = auth.createUser(TestAuthHelper.uniqueEmail());
		User other = auth.createUser(TestAuthHelper.uniqueEmail());
		DiaryEntryRequest req = new DiaryEntryRequest(DiaryMood.OK, Set.of(), null);
		diaryService.put(me.getId(), diaryService.today(), req);
		diaryService.put(other.getId(), diaryService.today(), req);
		TrialResponse mine = trialService.start(me.getId(),
				shelfService.add(me.getId(), fixtures.list(fixtures.product(), 1, "정제수").getId()).id());
		TrialResponse theirs = trialService.start(other.getId(),
				shelfService.add(other.getId(), fixtures.list(fixtures.product(), 1, "정제수").getId()).id());

		userService.withdraw(me.getId());

		assertThat(diaryRepository.findByUserIdAndEntryDate(me.getId(), diaryService.today())).isEmpty();
		assertThat(trialRepository.findById(mine.id())).isEmpty();
		assertThat(diaryRepository.findByUserIdAndEntryDate(other.getId(), diaryService.today())).isPresent();
		assertThat(trialRepository.findById(theirs.id())).isPresent();
	}
}
