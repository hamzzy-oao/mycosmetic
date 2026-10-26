package com.mycosmetic.service.extraction;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import com.mycosmetic.repository.ExtractionJobRepository;

/**
 * 추출 작업 정리 (설계 4.4, Review Focus 1). 10분마다, 그리고 서버가 뜬 직후 한 번 돈다.
 * <ol>
 * <li>확인·저장하지 않은 채 만든 지 24시간 지난 job을 지운다. 확인한 job은 결과 JSON이 이미 비어 있어 남긴다</li>
 * <li>RUNNING으로 바뀐 지(started_at) 5분 넘은 job → FAILED({@code SERVER_RESTARTED}), 한도 미차감.
 * 정상 작업은 AI 60초 × 2회 + 재시도 대기 2초라 5분 안에 끝난다</li>
 * <li>QUEUED이고 만든 지 5분 넘었으며 <b>이 서버가 뜨기 전에</b> 만든 job → 같은 FAILED.
 * 대기열(50개)은 메모리에만 있어 재시작하면 사라지지만, 이 서버가 뜬 뒤 만든 job은 지금 대기열에서
 * 차례를 기다리는 중일 수 있으므로 오래 기다려도 실패시키지 않는다</li>
 * </ol>
 * 실패시킨 뒤 작업 스레드가 늦게 끝나도 {@link ExtractionWorker}는 RUNNING인 job에만 결과를 적으므로 덮어쓰지 않는다.
 */
@Component
public class ExtractionCleanupScheduler {

	private static final Logger log = LoggerFactory.getLogger(ExtractionCleanupScheduler.class);

	static final String SERVER_RESTARTED = "SERVER_RESTARTED";
	static final Duration UNCONFIRMED_TTL = Duration.ofHours(24);
	static final Duration STALE_AFTER = Duration.ofMinutes(5);

	private final ExtractionJobRepository jobRepository;
	private final TransactionTemplate tx;
	private final Clock clock;
	private final boolean schedulingEnabled;
	/** 이 서버(프로세스)가 뜬 시각. 이보다 먼저 만든 QUEUED job은 이 서버의 대기열에 없다 */
	private final Instant serverStartedAt;

	public ExtractionCleanupScheduler(ExtractionJobRepository jobRepository, TransactionTemplate tx, Clock clock,
			@Value("${app.scheduling.enabled:true}") boolean schedulingEnabled) {
		this.jobRepository = jobRepository;
		this.tx = tx;
		this.clock = clock;
		this.schedulingEnabled = schedulingEnabled;
		this.serverStartedAt = clock.instant();
	}

	/** 재시작 전에 남은 고아 job을 10분 기다리지 않고 바로 정리한다. 스케줄링을 끈 테스트·평가 프로필에서는 하지 않는다 */
	@EventListener(ApplicationReadyEvent.class)
	public void cleanUpOnStartup() {
		if (schedulingEnabled) {
			cleanUp();
		}
	}

	@Scheduled(fixedDelayString = "PT10M", initialDelayString = "PT10M")
	public void cleanUp() {
		Instant now = clock.instant();
		Instant staleBefore = now.minus(STALE_AFTER);
		Instant queuedBefore = staleBefore.isBefore(serverStartedAt) ? staleBefore : serverStartedAt;

		Integer deleted = tx.execute(status -> jobRepository.deleteUnconfirmedCreatedBefore(now.minus(UNCONFIRMED_TTL)));
		Integer failedRunning = tx.execute(
				status -> jobRepository.failRunningStartedBefore(staleBefore, SERVER_RESTARTED, now));
		Integer failedQueued = tx.execute(
				status -> jobRepository.failQueuedCreatedBefore(queuedBefore, SERVER_RESTARTED, now));

		if (deleted + failedRunning + failedQueued > 0) {
			log.info("추출 작업 정리 deleted={} failedRunning={} failedQueued={}", deleted, failedRunning,
					failedQueued);
		}
	}
}
