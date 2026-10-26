package com.mycosmetic.service.diary;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneId;
import java.time.format.DateTimeParseException;
import java.util.List;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.mycosmetic.dto.diary.DiaryEntryRequest;
import com.mycosmetic.dto.diary.DiaryEntryResponse;
import com.mycosmetic.dto.diary.DiaryMonthResponse;
import com.mycosmetic.entity.SkinDiaryEntry;
import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.repository.SkinDiaryEntryRepository;

/**
 * 피부 일기(설계 2026-10-24 A). 🔒 항상 인증 사용자 ID로만 조회한다. 날짜는 Asia/Seoul 기준이다.
 */
@Service
public class SkinDiaryService {

	static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	/** 지난 날짜는 오늘부터 이만큼 전까지만 쓸 수 있다 */
	static final int MAX_PAST_DAYS = 365;

	private final SkinDiaryEntryRepository diaryRepository;
	private final TransactionTemplate transactionTemplate;
	private final Clock clock;

	public SkinDiaryService(SkinDiaryEntryRepository diaryRepository, PlatformTransactionManager transactionManager,
			Clock clock) {
		this.diaryRepository = diaryRepository;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	/** month는 YYYY-MM. 비었으면 이번 달, 형식이 틀리면 INVALID_REQUEST */
	@Transactional(readOnly = true)
	public DiaryMonthResponse month(Long userId, String month) {
		YearMonth ym = parseMonth(month);
		List<DiaryEntryResponse> entries = entriesBetween(userId, ym.atDay(1), ym.atEndOfMonth()).stream()
				.map(DiaryEntryResponse::of).toList();
		return new DiaryMonthResponse(ym.toString(), entries);
	}

	/**
	 * 그날 기록을 만들거나 통째로 바꾼다. 오늘보다 뒤이거나 1년보다 앞선 날이면 INVALID_REQUEST.
	 * 같은 날을 동시에 처음 써서 중복 불가 제약에 걸리면 한 번 다시 시도한다(이번엔 바꾸기가 된다)
	 */
	public DiaryEntryResponse put(Long userId, LocalDate date, DiaryEntryRequest request) {
		LocalDate today = today();
		if (date.isAfter(today) || date.isBefore(today.minusDays(MAX_PAST_DAYS))) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST);
		}
		String memo = request.memo() == null || request.memo().isBlank() ? null : request.memo().strip();
		try {
			return upsert(userId, date, request, memo);
		} catch (DataIntegrityViolationException e) {
			return upsert(userId, date, request, memo);
		}
	}

	private DiaryEntryResponse upsert(Long userId, LocalDate date, DiaryEntryRequest request, String memo) {
		return transactionTemplate.execute(status -> {
			SkinDiaryEntry entry = diaryRepository.findByUserIdAndEntryDate(userId, date).orElse(null);
			if (entry == null) {
				entry = SkinDiaryEntry.create(userId, date, request.mood(), request.tags(), memo);
			} else {
				entry.change(request.mood(), request.tags(), memo);
			}
			return DiaryEntryResponse.of(diaryRepository.saveAndFlush(entry));
		});
	}

	/** 기록이 없으면 NOT_FOUND */
	@Transactional
	public void delete(Long userId, LocalDate date) {
		diaryRepository.delete(diaryRepository.findByUserIdAndEntryDate(userId, date)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND)));
	}

	/** from·to 포함, 날짜순 */
	@Transactional(readOnly = true)
	public List<SkinDiaryEntry> entriesBetween(Long userId, LocalDate from, LocalDate to) {
		return diaryRepository.findByUserIdAndEntryDateBetweenOrderByEntryDate(userId, from, to);
	}

	@Transactional(readOnly = true)
	public boolean wroteToday(Long userId) {
		return diaryRepository.existsByUserIdAndEntryDate(userId, today());
	}

	public LocalDate today() {
		return LocalDate.now(clock.withZone(SEOUL));
	}

	private YearMonth parseMonth(String month) {
		if (month == null || month.isBlank()) {
			return YearMonth.from(today());
		}
		try {
			return YearMonth.parse(month.strip());
		} catch (DateTimeParseException e) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST);
		}
	}
}
