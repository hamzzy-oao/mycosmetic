package com.mycosmetic.service.extraction;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.function.Consumer;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.mycosmetic.client.ai.ExtractionClientException;
import com.mycosmetic.client.ai.ExtractionResult;
import com.mycosmetic.client.ai.IngredientExtractionClient;
import com.mycosmetic.config.AsyncConfig;
import com.mycosmetic.entity.ExtractionJob;
import com.mycosmetic.entity.JobStatus;
import com.mycosmetic.entity.MatchType;
import com.mycosmetic.repository.ExtractionJobRepository;
import com.mycosmetic.service.ingredient.IngredientMatcher;
import com.mycosmetic.service.ingredient.MatchResult;

import tools.jackson.databind.json.JsonMapper;

/**
 * 성분 추출 백그라운드 작업 (설계 4.1 4~6단계, 6.2).
 * RUNNING → AI 호출(재시도 가능한 실패면 retry-delay 뒤 1회 재시도) → 사전 매칭 → result_json 저장, DONE.
 * 최종 실패면 FAILED이고, 서버·AI 사정 실패는 하루 한도에서 뺀다(BAD_REQUEST만 센다, {@link #countsTowardLimit}).
 *
 * <p>사진 바이트는 메서드 인자로만 다룬다. 필드·DB·디스크·로그에 남기지 않는다.
 * AI 호출 중에는 DB 트랜잭션을 잡지 않는다(최대 2분 넘게 커넥션을 붙잡지 않도록).
 */
@Component
public class ExtractionWorker {

	private static final Logger log = LoggerFactory.getLogger(ExtractionWorker.class);

	static final String INTERNAL_ERROR = "INTERNAL_ERROR";

	private final IngredientExtractionClient client;
	private final IngredientMatcher matcher;
	private final ExtractionJobRepository jobRepository;
	private final TransactionTemplate tx;
	private final JsonMapper jsonMapper;
	private final Clock clock;
	private final Duration retryDelay;

	public ExtractionWorker(IngredientExtractionClient client, IngredientMatcher matcher,
			ExtractionJobRepository jobRepository, TransactionTemplate tx, JsonMapper jsonMapper, Clock clock,
			@Value("${app.extraction.retry-delay:2s}") Duration retryDelay) {
		this.client = client;
		this.matcher = matcher;
		this.jobRepository = jobRepository;
		this.tx = tx;
		this.jsonMapper = jsonMapper;
		this.clock = clock;
		this.retryDelay = retryDelay;
	}

	@Async(AsyncConfig.EXTRACTION_EXECUTOR)
	public void run(Long jobId, byte[] image, String mediaType) {
		try {
			if (!startIfQueued(jobId)) {
				// 큐에서 기다리는 동안 지워졌거나(탈퇴·정리) 이미 끝난 처리(오래된 job 정리)가 됨
				log.info("성분 추출 건너뜀 jobId={}", jobId);
				return;
			}
			ExtractionResult extracted;
			try {
				extracted = extractWithRetry(jobId, image, mediaType);
			} catch (ExtractionClientException e) {
				String errorCode = errorCodeFor(e.reason());
				boolean counted = countsTowardLimit(e.reason());
				log.warn("성분 추출 실패 jobId={} reason={} errorCode={} counted={}", jobId, e.reason(), errorCode,
						counted);
				finish(jobId, job -> job.fail(errorCode, counted, now()));
				return;
			}
			String resultJson = jsonMapper.writeValueAsString(toResult(extracted));
			finish(jobId, job -> job.complete(resultJson, now()));
			log.info("성분 추출 완료 jobId={} ingredients={}", jobId, extracted.ingredients().size());
		} catch (RuntimeException e) {
			log.error("성분 추출 작업 오류 jobId={}", jobId, e);
			try {
				finish(jobId, job -> job.fail(INTERNAL_ERROR, false, now()));
			} catch (RuntimeException again) {
				log.error("성분 추출 실패 기록 오류 jobId={}", jobId, again);
			}
		}
	}

	private ExtractionResult extractWithRetry(Long jobId, byte[] image, String mediaType) {
		try {
			return client.extract(image, mediaType);
		} catch (ExtractionClientException first) {
			if (!first.retryable()) {
				throw first;
			}
			log.info("성분 추출 재시도 jobId={} reason={}", jobId, first.reason());
			if (!sleep(retryDelay)) {
				throw first;
			}
			return client.extract(image, mediaType);
		}
	}

	ExtractionJobResult toResult(ExtractionResult extracted) {
		List<MatchResult> items = matcher.match(extracted.ingredients());
		return new ExtractionJobResult(extracted.ingredientSectionFound(), extracted.unreadableParts(),
				warningFor(extracted.ingredientSectionFound(), items), items);
	}

	/**
	 * 설계 6.2: 전성분 영역이 없으면 NO_INGREDIENT_SECTION, UNMATCHED가 30% 이상이면 MANY_UNMATCHED.
	 * 영역은 찾았다는데 읽은 성분이 하나도 없으면 확인할 것이 없으므로 NO_INGREDIENT_SECTION으로 본다.
	 */
	static String warningFor(boolean ingredientSectionFound, List<MatchResult> items) {
		if (!ingredientSectionFound || items.isEmpty()) {
			return ExtractionJobResult.NO_INGREDIENT_SECTION;
		}
		long unmatched = items.stream().filter(item -> item.type() == MatchType.UNMATCHED).count();
		// 부동소수점 대신 정수로 비교: unmatched / total >= 0.3
		return unmatched * 10 >= items.size() * 3L ? ExtractionJobResult.MANY_UNMATCHED : null;
	}

	static String errorCodeFor(ExtractionClientException.Reason reason) {
		return switch (reason) {
			case TIMEOUT -> "AI_TIMEOUT";
			case INVALID_RESPONSE -> "AI_INVALID_RESPONSE";
			case REFUSED -> "AI_REFUSED";
			case RATE_LIMITED, SERVER_ERROR, BAD_REQUEST, CLIENT_CONFIG -> "AI_UNAVAILABLE";
		};
	}

	/**
	 * 실패한 job을 하루 한도에 셀지. Claude 400(BAD_REQUEST)은 사용자가 같은 사진으로 되풀이해 일으킬 수 있어 센다.
	 * 거절(REFUSED)은 사용자 결정("Claude 거절 대비")대로 세지 않고, 타임아웃·429·5xx·JSON 오류와
	 * 그 밖의 4xx(CLIENT_CONFIG: 401·403·404·413 등 키·권한·설정 문제)는 서버 사정이라 세지 않는다.
	 */
	static boolean countsTowardLimit(ExtractionClientException.Reason reason) {
		return reason == ExtractionClientException.Reason.BAD_REQUEST;
	}

	/** QUEUED일 때만 RUNNING으로 바꾼다. 바꿨으면 true */
	private boolean startIfQueued(Long jobId) {
		Boolean started = tx.execute(status -> jobRepository.findById(jobId)
				.filter(job -> job.getStatus() == JobStatus.QUEUED)
				.map(job -> {
					job.start(now());
					return true;
				})
				.orElse(false));
		return Boolean.TRUE.equals(started);
	}

	/** 아직 RUNNING인 job에만 결과를 적는다(그 사이 지워졌거나 정리됐으면 아무것도 하지 않는다) */
	private void finish(Long jobId, Consumer<ExtractionJob> change) {
		tx.executeWithoutResult(status -> jobRepository.findById(jobId)
				.filter(job -> job.getStatus() == JobStatus.RUNNING)
				.ifPresent(change));
	}

	private Instant now() {
		return Instant.now(clock);
	}

	private static boolean sleep(Duration delay) {
		if (delay.isZero() || delay.isNegative()) {
			return true;
		}
		try {
			Thread.sleep(delay.toMillis());
			return true;
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
			return false;
		}
	}
}
