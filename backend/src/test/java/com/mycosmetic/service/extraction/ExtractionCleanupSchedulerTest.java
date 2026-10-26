package com.mycosmetic.service.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneId;
import java.time.ZoneOffset;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.transaction.support.TransactionTemplate;

import com.mycosmetic.entity.ExtractionJob;
import com.mycosmetic.entity.JobStatus;
import com.mycosmetic.entity.User;
import com.mycosmetic.repository.ExtractionJobRepository;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.TestAuthHelper;

/**
 * 설계 4.4(미확인 job 24시간 삭제)와 Review Focus 1(재시작으로 남은 job 정리).
 * 다른 테스트의 job을 건드리지 않게 2020년의 고정 시각을 쓴다(정리 기준이 그 시각 이전 행에만 걸린다).
 */
class ExtractionCleanupSchedulerTest extends IntegrationTestSupport {

	static final Instant SERVER_START = Instant.parse("2020-01-01T00:00:00Z");

	@Autowired
	ExtractionJobRepository jobRepository;

	@Autowired
	TransactionTemplate tx;

	@Autowired
	TestAuthHelper auth;

	MutableClock clock;
	ExtractionCleanupScheduler scheduler;
	User user;

	@BeforeEach
	void setUp() {
		clock = new MutableClock(SERVER_START);
		scheduler = new ExtractionCleanupScheduler(jobRepository, tx, clock, false);
		user = auth.createUser(TestAuthHelper.uniqueEmail());
	}

	private ExtractionJob queued(Instant createdAt) {
		return jobRepository.save(ExtractionJob.queue(user, createdAt));
	}

	private ExtractionJob running(Instant createdAt, Instant startedAt) {
		ExtractionJob job = ExtractionJob.queue(user, createdAt);
		job.start(startedAt);
		return jobRepository.save(job);
	}

	private ExtractionJob reload(ExtractionJob job) {
		return jobRepository.findById(job.getId()).orElseThrow();
	}

	@Test
	@DisplayName("24시간 지난 job은 지우고 23시간 59분 된 것은 남긴다")
	void deletesUnconfirmedJobsOlderThan24Hours() {
		clock.set(SERVER_START.plus(Duration.ofDays(2)));
		Instant now = clock.instant();
		ExtractionJob old = queued(now.minus(Duration.ofHours(24)).minusSeconds(1));
		old.complete("{}", now.minus(Duration.ofHours(24)));
		jobRepository.save(old);
		ExtractionJob young = jobRepository.save(doneJob(now.minus(Duration.ofHours(23)).minus(Duration.ofMinutes(59))));
		ExtractionJob confirmed = doneJob(now.minus(Duration.ofHours(30)));
		confirmed.markConfirmed(now.minus(Duration.ofHours(29)));
		confirmed = jobRepository.save(confirmed);

		scheduler.cleanUp();

		assertThat(jobRepository.findById(old.getId())).isEmpty();
		assertThat(jobRepository.findById(young.getId())).isPresent();
		assertThat(jobRepository.findById(confirmed.getId())).as("확인한 job은 남긴다").isPresent();
	}

	private ExtractionJob doneJob(Instant createdAt) {
		ExtractionJob job = ExtractionJob.queue(user, createdAt);
		job.complete("{}", createdAt.plusSeconds(30));
		return job;
	}

	@Test
	@DisplayName("staleRunningJobs_becomeFailedWithoutLimit")
	void staleRunningJobs_becomeFailedWithoutLimit() {
		clock.set(SERVER_START.plus(Duration.ofHours(1)));
		Instant now = clock.instant();
		// 대기열에서 오래 기다렸다가 4분 전에 시작한 job은 생성 시각이 오래돼도 그대로 둔다
		ExtractionJob stale = running(now.minus(Duration.ofMinutes(7)), now.minus(Duration.ofMinutes(6)));
		ExtractionJob fresh = running(now.minus(Duration.ofMinutes(20)), now.minus(Duration.ofMinutes(4)));

		scheduler.cleanUp();

		ExtractionJob failed = reload(stale);
		assertThat(failed.getStatus()).isEqualTo(JobStatus.FAILED);
		assertThat(failed.getErrorCode()).isEqualTo("SERVER_RESTARTED");
		assertThat(failed.isCountsTowardLimit()).isFalse();
		assertThat(failed.getFinishedAt()).isEqualTo(now);

		ExtractionJob untouched = reload(fresh);
		assertThat(untouched.getStatus()).isEqualTo(JobStatus.RUNNING);
		assertThat(untouched.isCountsTowardLimit()).isTrue();
	}

	@Test
	@DisplayName("서버가 뜨기 전에 만든 QUEUED job은 5분이 지나면 FAILED, 뜬 뒤 만든 것은 대기열에 있으므로 그대로")
	void orphanQueuedJobsFailOnlyIfCreatedBeforeServerStart() {
		ExtractionJob beforeStartOld = queued(SERVER_START.minus(Duration.ofMinutes(6)));
		ExtractionJob beforeStartYoung = queued(SERVER_START.minus(Duration.ofMinutes(1)));
		ExtractionJob afterStart = queued(SERVER_START.plusSeconds(1));

		// 서버가 뜬 직후: 6분 된 고아만 실패, 1분 된 고아는 아직 기다린다
		scheduler.cleanUp();
		assertThat(reload(beforeStartOld).getStatus()).isEqualTo(JobStatus.FAILED);
		assertThat(reload(beforeStartOld).getErrorCode()).isEqualTo("SERVER_RESTARTED");
		assertThat(reload(beforeStartOld).isCountsTowardLimit()).isFalse();
		assertThat(reload(beforeStartYoung).getStatus()).isEqualTo(JobStatus.QUEUED);

		// 30분 뒤: 서버가 뜨기 전에 만든 것은 실패, 뜬 뒤 만든 것은 대기열에서 차례를 기다리는 중이라 그대로
		clock.set(SERVER_START.plus(Duration.ofMinutes(30)));
		scheduler.cleanUp();
		assertThat(reload(beforeStartYoung).getStatus()).isEqualTo(JobStatus.FAILED);
		assertThat(reload(afterStart).getStatus()).isEqualTo(JobStatus.QUEUED);
		assertThat(reload(afterStart).isCountsTowardLimit()).isTrue();
	}

	@Test
	@DisplayName("스케줄링을 끈 프로필에서는 시작 직후 정리를 하지 않는다")
	void startupCleanupSkippedWhenSchedulingDisabled() {
		ExtractionJob orphan = queued(SERVER_START.minus(Duration.ofMinutes(10)));

		scheduler.cleanUpOnStartup();
		assertThat(reload(orphan).getStatus()).isEqualTo(JobStatus.QUEUED);

		new ExtractionCleanupScheduler(jobRepository, tx, clock, true).cleanUpOnStartup();
		assertThat(reload(orphan).getStatus()).isEqualTo(JobStatus.FAILED);
	}

	/** 테스트 안에서 시각을 옮길 수 있는 Clock */
	static final class MutableClock extends Clock {

		private Instant instant;

		MutableClock(Instant instant) {
			this.instant = instant;
		}

		void set(Instant instant) {
			this.instant = instant;
		}

		@Override
		public ZoneId getZone() {
			return ZoneOffset.UTC;
		}

		@Override
		public Clock withZone(ZoneId zone) {
			return this;
		}

		@Override
		public Instant instant() {
			return instant;
		}
	}
}
