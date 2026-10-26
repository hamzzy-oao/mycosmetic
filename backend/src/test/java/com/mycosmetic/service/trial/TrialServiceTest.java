package com.mycosmetic.service.trial;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.mycosmetic.dto.diary.DiaryEntryRequest;
import com.mycosmetic.dto.trial.TrialResponse;
import com.mycosmetic.dto.trial.TrialStatus;
import com.mycosmetic.dto.trial.TrialSummary;
import com.mycosmetic.entity.DiaryMood;
import com.mycosmetic.entity.DiaryTag;
import com.mycosmetic.entity.ProductTrial;
import com.mycosmetic.entity.ShelfItem;
import com.mycosmetic.entity.User;
import com.mycosmetic.entity.Verdict;
import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.repository.ProductTrialRepository;
import com.mycosmetic.repository.ShelfItemRepository;
import com.mycosmetic.service.diary.SkinDiaryService;
import com.mycosmetic.service.shelf.ShelfService;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.ProductFixtures;
import com.mycosmetic.support.TestAuthHelper;

/** 새 제품 2주 테스트 (설계 2026-10-24 B) */
class TrialServiceTest extends IntegrationTestSupport {

	@Autowired
	TrialService trialService;

	@Autowired
	ProductTrialRepository trialRepository;

	@Autowired
	ShelfItemRepository shelfItemRepository;

	@Autowired
	ShelfService shelfService;

	@Autowired
	SkinDiaryService diaryService;

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

	/** 지난 날짜에 시작한 테스트를 바로 만든다 */
	private ProductTrial pastTrial(User user, Long shelfItemId, LocalDate startedOn) {
		ShelfItem item = shelfItemRepository.findById(shelfItemId).orElseThrow();
		return trialRepository.saveAndFlush(
				ProductTrial.start(user.getId(), item, startedOn, startedOn.plusDays(TrialService.TRIAL_DAYS - 1)));
	}

	private static ErrorCode codeOf(Throwable e) {
		return ((BusinessException) e).errorCode();
	}

	@Test
	@DisplayName("시작: 오늘부터 14일, 1일째, 진행 중")
	void start() {
		User user = newUser();
		Long itemId = shelfItem(user);
		LocalDate today = diaryService.today();

		TrialResponse trial = trialService.start(user.getId(), itemId);

		assertThat(trial.shelfItemId()).isEqualTo(itemId);
		assertThat(trial.startedOn()).isEqualTo(today);
		assertThat(trial.endsOn()).isEqualTo(today.plusDays(13));
		assertThat(trial.day()).isEqualTo(1);
		assertThat(trial.totalDays()).isEqualTo(14);
		assertThat(trial.status()).isEqualTo(TrialStatus.ACTIVE);
		assertThat(trial.verdict()).isEqualTo(Verdict.UNRATED);
		assertThat(trial.product().representativeListId()).isNotNull();
	}

	@Test
	@DisplayName("시작: 남의 항목은 NOT_FOUND, 이미 테스트한 항목은 TRIAL_DUPLICATE")
	void start_errors() {
		User a = newUser();
		User b = newUser();
		Long aItem = shelfItem(a);
		trialService.start(a.getId(), aItem);

		assertThatThrownBy(() -> trialService.start(b.getId(), aItem))
				.satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.NOT_FOUND));
		assertThatThrownBy(() -> trialService.start(a.getId(), aItem))
				.satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.TRIAL_DUPLICATE));
	}

	@Test
	@DisplayName("끝내기: 평가를 바꾸고 사용 시작일이 비었으면 테스트 시작일로 채운다. 맞음·안 맞음만 되고 보통·평가 전은 INVALID_REQUEST")
	void finish() {
		User user = newUser();
		Long itemId = shelfItem(user);
		ProductTrial trial = pastTrial(user, itemId, diaryService.today().minusDays(3));

		assertThatThrownBy(() -> trialService.finish(user.getId(), trial.getId(), Verdict.UNRATED))
				.satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_REQUEST));
		assertThatThrownBy(() -> trialService.finish(user.getId(), trial.getId(), Verdict.NEUTRAL))
				.satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_REQUEST));
		TrialResponse done = trialService.finish(user.getId(), trial.getId(), Verdict.BAD);

		assertThat(done.status()).isEqualTo(TrialStatus.DONE);
		assertThat(done.finishedOn()).isEqualTo(diaryService.today());
		assertThat(done.verdict()).isEqualTo(Verdict.BAD);
		ShelfItem item = shelfItemRepository.findById(itemId).orElseThrow();
		assertThat(item.getVerdict()).isEqualTo(Verdict.BAD);
		assertThat(item.getStartedOn()).isEqualTo(trial.getStartedOn());
		assertThat(trialRepository.findById(trial.getId()).orElseThrow().getFinishedAt()).isNotNull();
	}

	@Test
	@DisplayName("목록: 평가 대기 → 진행 중 → 끝낸 것 순, 기간이 지나면 READY, day는 14를 넘지 않는다")
	void list_orderAndStatus() {
		User user = newUser();
		LocalDate today = diaryService.today();
		ProductTrial ready = pastTrial(user, shelfItem(user), today.minusDays(20));
		TrialResponse active = trialService.start(user.getId(), shelfItem(user));
		ProductTrial done = pastTrial(user, shelfItem(user), today.minusDays(5));
		trialService.finish(user.getId(), done.getId(), Verdict.GOOD);

		List<TrialResponse> list = trialService.list(user.getId());

		assertThat(list).extracting(TrialResponse::id).containsExactly(ready.getId(), active.id(), done.getId());
		assertThat(list).extracting(TrialResponse::status)
				.containsExactly(TrialStatus.READY, TrialStatus.ACTIVE, TrialStatus.DONE);
		assertThat(list.get(0).day()).isEqualTo(14);
		assertThat(list.get(2).day()).isEqualTo(6);
	}

	@Test
	@DisplayName("요약: 테스트 기간 안의 일기만 기분·태그별로 센다")
	void summary_countsDiaryInWindow() {
		User user = newUser();
		LocalDate today = diaryService.today();
		LocalDate start = today.minusDays(20);
		ProductTrial trial = pastTrial(user, shelfItem(user), start);
		diaryService.put(user.getId(), start.minusDays(1), new DiaryEntryRequest(DiaryMood.BAD, Set.of(DiaryTag.TROUBLE), null));
		diaryService.put(user.getId(), start, new DiaryEntryRequest(DiaryMood.BAD, Set.of(DiaryTag.TROUBLE, DiaryTag.REDNESS), null));
		diaryService.put(user.getId(), start.plusDays(5), new DiaryEntryRequest(DiaryMood.GOOD, Set.of(), null));
		diaryService.put(user.getId(), start.plusDays(13), new DiaryEntryRequest(DiaryMood.OK, Set.of(DiaryTag.TROUBLE), null));
		diaryService.put(user.getId(), start.plusDays(14), new DiaryEntryRequest(DiaryMood.BAD, Set.of(DiaryTag.TROUBLE), null));

		TrialSummary summary = trialService.list(user.getId()).stream().filter(t -> t.id().equals(trial.getId()))
				.findFirst().orElseThrow().summary();

		assertThat(summary.recordedDays()).isEqualTo(3);
		assertThat(summary.moods()).containsEntry(DiaryMood.BAD, 1L).containsEntry(DiaryMood.GOOD, 1L)
				.containsEntry(DiaryMood.OK, 1L);
		assertThat(summary.tags()).containsEntry(DiaryTag.TROUBLE, 2L).containsEntry(DiaryTag.REDNESS, 1L)
				.containsEntry(DiaryTag.DRYNESS, 0L);
	}

	@Test
	@DisplayName("14일 칸: 날마다 그날 일기 기분, 기록 안 한 날·아직 안 온 날·일찍 끝낸 뒤는 null")
	void days_perDayMoods() {
		User user = newUser();
		LocalDate today = diaryService.today();
		LocalDate start = today.minusDays(4);
		ProductTrial trial = pastTrial(user, shelfItem(user), start);
		diaryService.put(user.getId(), start, new DiaryEntryRequest(DiaryMood.GOOD, Set.of(), null));
		diaryService.put(user.getId(), start.plusDays(2), new DiaryEntryRequest(DiaryMood.BAD, Set.of(), null));
		diaryService.put(user.getId(), today, new DiaryEntryRequest(DiaryMood.OK, Set.of(), null));

		TrialResponse active = trialService.list(user.getId()).get(0);

		assertThat(active.days()).hasSize(14);
		assertThat(active.days().get(0).date()).isEqualTo(start);
		assertThat(active.days()).extracting(d -> d.mood()).startsWith(DiaryMood.GOOD, null, DiaryMood.BAD, null,
				DiaryMood.OK, null);
		assertThat(active.finishedOn()).isNull();

		// 오늘 끝내면 오늘까지만 센다(오늘 일기 포함)
		TrialResponse done = trialService.finish(user.getId(), trial.getId(), Verdict.GOOD);
		assertThat(done.summary().recordedDays()).isEqualTo(3);
		assertThat(done.days().get(4).mood()).isEqualTo(DiaryMood.OK);
		assertThat(done.days().subList(5, 14)).allSatisfy(d -> assertThat(d.mood()).isNull());
	}

	@Test
	@DisplayName("취소: 테스트만 지우고 화장대 항목은 남는다. 남의 테스트는 NOT_FOUND")
	void cancel() {
		User a = newUser();
		User b = newUser();
		Long itemId = shelfItem(a);
		TrialResponse trial = trialService.start(a.getId(), itemId);

		assertThatThrownBy(() -> trialService.cancel(b.getId(), trial.id()))
				.satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.NOT_FOUND));
		trialService.cancel(a.getId(), trial.id());

		assertThat(trialRepository.findById(trial.id())).isEmpty();
		assertThat(shelfItemRepository.findById(itemId)).isPresent();
		assertThat(trialService.list(a.getId())).isEmpty();
	}
}
