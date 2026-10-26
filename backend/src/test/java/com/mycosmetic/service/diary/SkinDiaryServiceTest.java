package com.mycosmetic.service.diary;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.mycosmetic.dto.diary.DiaryEntryRequest;
import com.mycosmetic.dto.diary.DiaryEntryResponse;
import com.mycosmetic.dto.diary.DiaryMonthResponse;
import com.mycosmetic.entity.DiaryMood;
import com.mycosmetic.entity.DiaryTag;
import com.mycosmetic.entity.User;
import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.repository.SkinDiaryEntryRepository;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.TestAuthHelper;

/** 피부 일기 (설계 2026-10-24 A) */
class SkinDiaryServiceTest extends IntegrationTestSupport {

	@Autowired
	SkinDiaryService diaryService;

	@Autowired
	SkinDiaryEntryRepository diaryRepository;

	@Autowired
	TestAuthHelper auth;

	private User newUser() {
		return auth.createUser(TestAuthHelper.uniqueEmail());
	}

	private static ErrorCode codeOf(Throwable e) {
		return ((BusinessException) e).errorCode();
	}

	@Test
	@DisplayName("같은 날 다시 쓰면 한 줄을 통째로 바꾼다. 빈 메모는 지운다")
	void put_upserts() {
		User user = newUser();
		LocalDate today = diaryService.today();

		diaryService.put(user.getId(), today, new DiaryEntryRequest(DiaryMood.BAD, Set.of(DiaryTag.TROUBLE), "메모"));
		DiaryEntryResponse changed = diaryService.put(user.getId(), today,
				new DiaryEntryRequest(DiaryMood.GOOD, null, "  "));

		assertThat(changed.mood()).isEqualTo(DiaryMood.GOOD);
		assertThat(changed.tags()).isEmpty();
		assertThat(changed.memo()).isNull();
		assertThat(diaryRepository.findByUserIdAndEntryDateBetweenOrderByEntryDate(user.getId(), today, today))
				.hasSize(1);
	}

	@Test
	@DisplayName("오늘보다 뒤이거나 1년보다 앞선 날은 INVALID_REQUEST, 딱 1년 전은 된다")
	void put_dateRange() {
		User user = newUser();
		LocalDate today = diaryService.today();
		DiaryEntryRequest req = new DiaryEntryRequest(DiaryMood.OK, Set.of(), null);

		assertThatThrownBy(() -> diaryService.put(user.getId(), today.plusDays(1), req))
				.satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_REQUEST));
		assertThatThrownBy(() -> diaryService.put(user.getId(), today.minusDays(366), req))
				.satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_REQUEST));
		assertThat(diaryService.put(user.getId(), today.minusDays(365), req).date()).isEqualTo(today.minusDays(365));
	}

	@Test
	@DisplayName("월 조회는 그 달 기록만 날짜순으로, month가 없으면 이번 달, 형식이 틀리면 INVALID_REQUEST")
	void month() {
		User user = newUser();
		LocalDate today = diaryService.today();
		YearMonth thisMonth = YearMonth.from(today);
		LocalDate first = thisMonth.atDay(1);
		DiaryEntryRequest req = new DiaryEntryRequest(DiaryMood.OK, Set.of(), null);
		diaryService.put(user.getId(), today, req);
		diaryService.put(user.getId(), first, req);
		diaryService.put(user.getId(), first.minusDays(1), req);

		DiaryMonthResponse month = diaryService.month(user.getId(), null);

		assertThat(month.month()).isEqualTo(thisMonth.toString());
		assertThat(month.entries()).extracting(DiaryEntryResponse::date)
				.containsExactlyElementsOf(today.equals(first) ? List.of(first) : List.of(first, today));
		assertThat(diaryService.month(user.getId(), thisMonth.minusMonths(1).toString()).entries())
				.extracting(DiaryEntryResponse::date).containsExactly(first.minusDays(1));
		assertThatThrownBy(() -> diaryService.month(user.getId(), "2026/10"))
				.satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_REQUEST));
	}

	@Test
	@DisplayName("지우기: 있으면 지우고, 없으면 NOT_FOUND. wroteToday는 오늘 기록이 있는지")
	void delete_andWroteToday() {
		User user = newUser();
		LocalDate today = diaryService.today();
		assertThat(diaryService.wroteToday(user.getId())).isFalse();
		diaryService.put(user.getId(), today, new DiaryEntryRequest(DiaryMood.OK, Set.of(), null));
		assertThat(diaryService.wroteToday(user.getId())).isTrue();

		diaryService.delete(user.getId(), today);

		assertThat(diaryService.wroteToday(user.getId())).isFalse();
		assertThatThrownBy(() -> diaryService.delete(user.getId(), today))
				.satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.NOT_FOUND));
	}
}
