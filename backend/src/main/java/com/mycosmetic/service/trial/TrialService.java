package com.mycosmetic.service.trial;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.mycosmetic.dto.product.ProductSummaryResponse;
import com.mycosmetic.dto.trial.TrialDay;
import com.mycosmetic.dto.trial.TrialResponse;
import com.mycosmetic.dto.trial.TrialStatus;
import com.mycosmetic.dto.trial.TrialSummary;
import com.mycosmetic.entity.DiaryMood;
import com.mycosmetic.entity.DiaryTag;
import com.mycosmetic.entity.ProductTrial;
import com.mycosmetic.entity.ShelfItem;
import com.mycosmetic.entity.SkinDiaryEntry;
import com.mycosmetic.entity.Verdict;
import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.repository.ProductTrialRepository;
import com.mycosmetic.repository.ShelfItemRepository;
import com.mycosmetic.service.diary.SkinDiaryService;
import com.mycosmetic.service.product.ProductQueryService;

/**
 * 새 제품 2주 테스트(설계 2026-10-24 B). 기간 동안의 기록은 피부 일기를 센다.
 * 🔒 항상 인증 사용자 ID로만 조회하고, 남의 테스트·화장대 항목 ID는 NOT_FOUND로 답한다.
 */
@Service
public class TrialService {

	/** 테스트 기간(일, 시작일 포함) */
	static final int TRIAL_DAYS = 14;

	private static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private final ProductTrialRepository trialRepository;
	private final ShelfItemRepository shelfItemRepository;
	private final SkinDiaryService diaryService;
	private final ProductQueryService productQueryService;
	private final TransactionTemplate transactionTemplate;
	private final Clock clock;

	public TrialService(ProductTrialRepository trialRepository, ShelfItemRepository shelfItemRepository,
			SkinDiaryService diaryService, ProductQueryService productQueryService,
			PlatformTransactionManager transactionManager, Clock clock) {
		this.trialRepository = trialRepository;
		this.shelfItemRepository = shelfItemRepository;
		this.diaryService = diaryService;
		this.productQueryService = productQueryService;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	/** 평가 대기 → 진행 중 → 끝낸 것 순, 같은 상태는 최근에 시작한 순. 일기는 한 번에 읽는다 */
	@Transactional(readOnly = true)
	public List<TrialResponse> list(Long userId) {
		List<ProductTrial> trials = trialRepository.findWithProductByUserId(userId);
		if (trials.isEmpty()) {
			return List.of();
		}
		LocalDate today = diaryService.today();
		LocalDate from = trials.stream().map(ProductTrial::getStartedOn).min(Comparator.naturalOrder()).orElseThrow();
		List<SkinDiaryEntry> entries = from.isAfter(today) ? List.of()
				: diaryService.entriesBetween(userId, from, today);
		Map<Long, Long> reps = productQueryService.representativeListIds(
				trials.stream().map(t -> t.getShelfItem().getProduct().getId()).distinct().toList());
		return trials.stream().map(t -> toResponse(t, today, entries, reps))
				.sorted(Comparator.comparing(TrialResponse::status)).toList();
	}

	/** 오늘 시작해서 14일. 남의 항목이면 NOT_FOUND, 이미 테스트한 항목이면(동시에 시작해도) TRIAL_DUPLICATE */
	public TrialResponse start(Long userId, Long shelfItemId) {
		try {
			return transactionTemplate.execute(status -> {
				ShelfItem item = shelfItemRepository.findByIdAndUserId(shelfItemId, userId)
						.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
				if (trialRepository.existsByShelfItemId(shelfItemId)) {
					throw new BusinessException(ErrorCode.TRIAL_DUPLICATE);
				}
				LocalDate today = diaryService.today();
				ProductTrial trial = trialRepository
						.saveAndFlush(ProductTrial.start(userId, item, today, today.plusDays(TRIAL_DAYS - 1)));
				return toResponse(trial, userId);
			});
		} catch (DataIntegrityViolationException e) {
			throw new BusinessException(ErrorCode.TRIAL_DUPLICATE);
		}
	}

	/**
	 * 화장대 항목의 평가를 바꾸고 테스트를 끝낸다. 기간 전에도, 이미 끝낸 뒤에 다시 해도 된다.
	 * 항목의 사용 시작일이 비어 있으면 테스트 시작일로 채운다. 맞음·안 맞음만 되고 그 밖이면 INVALID_REQUEST
	 */
	@Transactional
	public TrialResponse finish(Long userId, Long trialId, Verdict verdict) {
		if (verdict != Verdict.GOOD && verdict != Verdict.BAD) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST);
		}
		ProductTrial trial = find(userId, trialId);
		ShelfItem item = trial.getShelfItem();
		LocalDate startedOn = item.getStartedOn() != null ? item.getStartedOn() : trial.getStartedOn();
		item.update(verdict, item.getMemo(), startedOn, item.getEndedOn());
		trial.finish(clock.instant());
		trialRepository.flush();
		return toResponse(trial, userId);
	}

	/** 테스트만 지운다. 화장대 항목과 일기는 그대로 */
	@Transactional
	public void cancel(Long userId, Long trialId) {
		trialRepository.delete(find(userId, trialId));
	}

	private ProductTrial find(Long userId, Long trialId) {
		return trialRepository.findByIdAndUserId(trialId, userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
	}

	private TrialResponse toResponse(ProductTrial trial, Long userId) {
		LocalDate today = diaryService.today();
		List<SkinDiaryEntry> entries = trial.getStartedOn().isAfter(today) ? List.of()
				: diaryService.entriesBetween(userId, trial.getStartedOn(), today);
		Long productId = trial.getShelfItem().getProduct().getId();
		return toResponse(trial, today, entries, productQueryService.representativeListIds(List.of(productId)));
	}

	private static TrialResponse toResponse(ProductTrial trial, LocalDate today, List<SkinDiaryEntry> entries,
			Map<Long, Long> reps) {
		ShelfItem item = trial.getShelfItem();
		long elapsed = ChronoUnit.DAYS.between(trial.getStartedOn(), today) + 1;
		int day = (int) Math.max(1, Math.min(TRIAL_DAYS, elapsed));
		LocalDate finishedOn = finishedOn(trial);
		return new TrialResponse(trial.getId(), item.getId(),
				ProductSummaryResponse.of(item.getProduct(), reps.get(item.getProduct().getId())),
				trial.getStartedOn(), trial.getEndsOn(), finishedOn, day, TRIAL_DAYS, status(trial, today),
				item.getVerdict(), summarize(trial, today, entries), days(trial, today, entries));
	}

	private static LocalDate finishedOn(ProductTrial trial) {
		return trial.getFinishedAt() == null ? null : LocalDate.ofInstant(trial.getFinishedAt(), SEOUL);
	}

	/** 테스트 기간의 마지막 날: 종료일, 오늘, 일찍 끝낸 날 중 가장 이른 날 */
	static LocalDate lastCountedDay(ProductTrial trial, LocalDate today) {
		LocalDate to = today.isBefore(trial.getEndsOn()) ? today : trial.getEndsOn();
		LocalDate finished = finishedOn(trial);
		return finished != null && finished.isBefore(to) ? finished : to;
	}

	/** 시작일부터 14일. 기간 안에 쓴 일기만 기분을 채운다 */
	static List<TrialDay> days(ProductTrial trial, LocalDate today, List<SkinDiaryEntry> entries) {
		LocalDate to = lastCountedDay(trial, today);
		Map<LocalDate, DiaryMood> moods = new HashMap<>();
		for (SkinDiaryEntry e : entries) {
			moods.put(e.getEntryDate(), e.getMood());
		}
		List<TrialDay> days = new ArrayList<>(TRIAL_DAYS);
		for (int i = 0; i < TRIAL_DAYS; i++) {
			LocalDate date = trial.getStartedOn().plusDays(i);
			days.add(new TrialDay(date, date.isAfter(to) ? null : moods.get(date)));
		}
		return List.copyOf(days);
	}

	static TrialStatus status(ProductTrial trial, LocalDate today) {
		if (trial.getFinishedAt() != null) {
			return TrialStatus.DONE;
		}
		return today.isAfter(trial.getEndsOn()) ? TrialStatus.READY : TrialStatus.ACTIVE;
	}

	/** 시작일부터 종료일(아직 안 끝났으면 오늘, 일찍 끝냈으면 끝낸 날)까지의 일기만 센다 */
	static TrialSummary summarize(ProductTrial trial, LocalDate today, List<SkinDiaryEntry> entries) {
		LocalDate to = lastCountedDay(trial, today);
		Map<DiaryMood, Long> moods = new EnumMap<>(DiaryMood.class);
		for (DiaryMood m : DiaryMood.values()) {
			moods.put(m, 0L);
		}
		Map<DiaryTag, Long> tags = new EnumMap<>(DiaryTag.class);
		for (DiaryTag t : DiaryTag.values()) {
			tags.put(t, 0L);
		}
		int recorded = 0;
		for (SkinDiaryEntry e : entries) {
			if (e.getEntryDate().isBefore(trial.getStartedOn()) || e.getEntryDate().isAfter(to)) {
				continue;
			}
			recorded++;
			moods.merge(e.getMood(), 1L, Long::sum);
			e.getTags().forEach(t -> tags.merge(t, 1L, Long::sum));
		}
		return new TrialSummary(recorded, moods, tags);
	}
}
