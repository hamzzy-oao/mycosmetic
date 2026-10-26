package com.mycosmetic.service.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.CyclicBarrier;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import com.mycosmetic.entity.ExtractionJob;
import com.mycosmetic.entity.JobStatus;
import com.mycosmetic.entity.User;
import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.repository.ExtractionJobRepository;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.TestAuthHelper;
import com.mycosmetic.support.TestImages;

/** 설계 4.1 3단계(접수)와 4.4 하루 한도. worker는 가짜로 바꿔 AI를 부르지 않는다 */
class ExtractionServiceTest extends IntegrationTestSupport {

	static final byte[] JPEG = TestImages.jpeg();
	static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	@Autowired
	ExtractionService extractionService;

	@Autowired
	ExtractionJobRepository jobRepository;

	@Autowired
	TestAuthHelper auth;

	@Autowired
	Clock clock;

	@MockitoBean
	ExtractionWorker worker;

	private static MockMultipartFile jpeg() {
		return new MockMultipartFile("image", "label.jpg", "image/jpeg", JPEG);
	}

	private User newUser() {
		return auth.createUser(TestAuthHelper.uniqueEmail());
	}

	private Instant todayStart() {
		return LocalDate.now(clock.withZone(SEOUL)).atStartOfDay(SEOUL).toInstant();
	}

	/** 한도에 포함되는 끝난(DONE) job을 count개 미리 만든다. 진행 중이 아니므로 동시 작업 제한에는 걸리지 않는다 */
	private void seedJobs(User user, int count, Instant createdAt) {
		List<ExtractionJob> jobs = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			ExtractionJob job = ExtractionJob.queue(user, createdAt);
			job.complete("{}", createdAt);
			jobs.add(job);
		}
		jobRepository.saveAll(jobs);
	}

	/** worker가 가짜라 QUEUED로 남은 job을 DONE으로 끝낸다(다음 접수가 동시 작업 제한에 걸리지 않게) */
	private void finish(Long jobId) {
		ExtractionJob job = jobRepository.findById(jobId).orElseThrow();
		job.complete("{}", Instant.now(clock));
		jobRepository.save(job);
	}

	private static ErrorCode errorCodeOf(Throwable e) {
		return ((BusinessException) e).errorCode();
	}

	private long countedToday(User user) {
		return jobRepository.countByUserIdAndCountsTowardLimitTrueAndCreatedAtGreaterThanEqual(user.getId(),
				todayStart());
	}

	@Test
	@DisplayName("접수하면 QUEUED job을 만들고 커밋 뒤 worker에 사진을 넘긴다")
	void accept_createsQueuedJobAndDispatches() {
		User user = newUser();

		Long jobId = extractionService.accept(user.getId(), jpeg());

		ExtractionJob job = jobRepository.findByIdAndUserId(jobId, user.getId()).orElseThrow();
		assertThat(job.getStatus()).isEqualTo(JobStatus.QUEUED);
		assertThat(job.isCountsTowardLimit()).isTrue();
		assertThat(job.getResultJson()).isNull();
		verify(worker).run(eq(jobId), eq(JPEG), eq("image/jpeg"));
	}

	@Test
	@DisplayName("20번째까지 접수되고 21번째는 EXTRACTION_DAILY_LIMIT")
	void dailyLimit_21stRejected() {
		User user = newUser();
		for (int i = 0; i < 20; i++) {
			finish(extractionService.accept(user.getId(), jpeg()));
		}

		assertThatThrownBy(() -> extractionService.accept(user.getId(), jpeg()))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).errorCode())
				.isEqualTo(ErrorCode.EXTRACTION_DAILY_LIMIT);
		assertThat(countedToday(user)).isEqualTo(20);
	}

	@Test
	@DisplayName("실패한 추출(counts_toward_limit=false)은 한도에 세지 않는다")
	void failedJobs_notCounted() {
		User user = newUser();
		List<ExtractionJob> failed = new ArrayList<>();
		for (int i = 0; i < 20; i++) {
			ExtractionJob job = ExtractionJob.queue(user, Instant.now(clock));
			job.fail("AI_UNAVAILABLE", false, Instant.now(clock));
			failed.add(job);
		}
		jobRepository.saveAll(failed);

		Long jobId = extractionService.accept(user.getId(), jpeg());

		assertThat(jobId).isNotNull();
	}

	@Test
	@DisplayName("어제(Asia/Seoul) 만든 job은 오늘 한도에 세지 않는다")
	void yesterdayJobs_notCounted() {
		User user = newUser();
		// 오늘 0시(서울) 1초 전 = 어제
		seedJobs(user, 20, todayStart().minus(Duration.ofSeconds(1)));

		Long jobId = extractionService.accept(user.getId(), jpeg());

		assertThat(jobId).isNotNull();
		assertThat(countedToday(user)).isEqualTo(1);
	}

	@Test
	@DisplayName("concurrentAcceptAtLimit_onlyOnePasses")
	void concurrentAcceptAtLimit_onlyOnePasses() throws Exception {
		// Review Focus 2: 19건 있는 상태에서 두 탭이 동시에 20번째를 접수
		User user = newUser();
		seedJobs(user, 19, Instant.now(clock));
		CyclicBarrier barrier = new CyclicBarrier(2);
		Callable<Long> acceptTask = () -> {
			barrier.await(5, TimeUnit.SECONDS);
			return extractionService.accept(user.getId(), jpeg());
		};

		ExecutorService pool = Executors.newFixedThreadPool(2);
		List<Future<Long>> futures;
		try {
			futures = List.of(pool.submit(acceptTask), pool.submit(acceptTask));
			pool.shutdown();
			assertThat(pool.awaitTermination(30, TimeUnit.SECONDS)).isTrue();
		} finally {
			pool.shutdownNow();
		}

		int passed = 0;
		List<ErrorCode> rejected = new ArrayList<>();
		for (Future<Long> future : futures) {
			try {
				future.get();
				passed++;
			} catch (ExecutionException e) {
				assertThat(e.getCause()).isInstanceOf(BusinessException.class);
				rejected.add(((BusinessException) e.getCause()).errorCode());
			}
		}
		assertThat(passed).isEqualTo(1);
		assertThat(rejected).containsExactly(ErrorCode.EXTRACTION_DAILY_LIMIT);
		assertThat(countedToday(user)).isEqualTo(20);
	}

	@Test
	@DisplayName("내용이 JPEG·PNG가 아니면 UNSUPPORTED_IMAGE이고 job을 만들지 않는다")
	void notAnImage_rejectedWithoutJob() {
		User user = newUser();
		MockMultipartFile pdf = new MockMultipartFile("image", "label.jpg", "image/jpeg", "%PDF-1.7".getBytes());

		assertThatThrownBy(() -> extractionService.accept(user.getId(), pdf))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).errorCode())
				.isEqualTo(ErrorCode.UNSUPPORTED_IMAGE);
		assertThat(countedToday(user)).isZero();
		verify(worker, never()).run(any(), any(), any());
	}

	@Test
	@DisplayName("5MB를 넘으면 FILE_TOO_LARGE")
	void tooLarge_rejected() {
		User user = newUser();
		byte[] big = new byte[5 * 1024 * 1024 + 1];
		System.arraycopy(JPEG, 0, big, 0, JPEG.length);

		assertThatThrownBy(() -> extractionService.accept(user.getId(),
				new MockMultipartFile("image", "big.jpg", "image/jpeg", big)))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).errorCode())
				.isEqualTo(ErrorCode.FILE_TOO_LARGE);
		assertThat(countedToday(user)).isZero();
	}

	@Test
	@DisplayName("작업 큐가 차면 AI_BUSY이고 그 job은 FAILED로 한도에서 빠진다")
	void executorFull_aiBusyWithoutLimit() {
		User user = newUser();
		doThrow(new TaskRejectedException("full")).when(worker).run(any(), any(), any());

		assertThatThrownBy(() -> extractionService.accept(user.getId(), jpeg()))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).errorCode())
				.isEqualTo(ErrorCode.AI_BUSY);

		assertThat(countedToday(user)).isZero();
		List<ExtractionJob> jobs = jobRepository.findAll().stream()
				.filter(job -> job.getUser().getId().equals(user.getId()))
				.toList();
		assertThat(jobs).singleElement().satisfies(job -> {
			assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
			assertThat(job.getErrorCode()).isEqualTo("AI_BUSY");
		});
	}

	@Test
	@DisplayName("진행 중(QUEUED·RUNNING)인 job이 2개면 3번째는 EXTRACTION_IN_PROGRESS이고 job을 만들지 않는다")
	void inProgressCap_thirdRejected() {
		User user = newUser();
		Long first = extractionService.accept(user.getId(), jpeg());
		extractionService.accept(user.getId(), jpeg());

		assertThatThrownBy(() -> extractionService.accept(user.getId(), jpeg()))
				.isInstanceOf(BusinessException.class)
				.extracting(ExtractionServiceTest::errorCodeOf)
				.isEqualTo(ErrorCode.EXTRACTION_IN_PROGRESS);
		assertThat(countedToday(user)).isEqualTo(2);

		// 하나가 끝나면 다시 접수할 수 있다
		finish(first);
		assertThat(extractionService.accept(user.getId(), jpeg())).isNotNull();
	}

	@Test
	@DisplayName("다른 사용자의 진행 중 job은 내 동시 작업 수에 세지 않는다")
	void inProgressCap_perUser() {
		User a = newUser();
		User b = newUser();
		extractionService.accept(a.getId(), jpeg());
		extractionService.accept(a.getId(), jpeg());

		assertThat(extractionService.accept(b.getId(), jpeg())).isNotNull();
	}

	@Test
	@DisplayName("머리에 적힌 세로가 4000px를 넘으면 IMAGE_DIMENSIONS_TOO_LARGE이고 job을 만들지 않는다")
	void tallImageHeader_rejectedWithoutJob() {
		User user = newUser();
		MockMultipartFile tall = new MockMultipartFile("image", "tall.png", "image/png",
				TestImages.pngHeaderOnly(1, 20000));

		assertThatThrownBy(() -> extractionService.accept(user.getId(), tall))
				.isInstanceOf(BusinessException.class)
				.extracting(ExtractionServiceTest::errorCodeOf)
				.isEqualTo(ErrorCode.IMAGE_DIMENSIONS_TOO_LARGE);
		assertThat(countedToday(user)).isZero();
		verify(worker, never()).run(any(), any(), any());
	}

	@Test
	@DisplayName("가로·세로 4000px까지는 받는다")
	void imageAtDimensionLimit_accepted() {
		User user = newUser();
		MockMultipartFile edge = new MockMultipartFile("image", "edge.png", "image/png",
				TestImages.pngHeaderOnly(4000, 4000));

		assertThat(extractionService.accept(user.getId(), edge)).isNotNull();
	}

	@Test
	@DisplayName("시그니처만 JPEG이고 머리를 읽을 수 없으면 UNSUPPORTED_IMAGE")
	void unreadableJpeg_rejected() {
		User user = newUser();
		byte[] broken = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0, 0x00, 0x10 };

		assertThatThrownBy(() -> extractionService.accept(user.getId(),
				new MockMultipartFile("image", "broken.jpg", "image/jpeg", broken)))
				.isInstanceOf(BusinessException.class)
				.extracting(ExtractionServiceTest::errorCodeOf)
				.isEqualTo(ErrorCode.UNSUPPORTED_IMAGE);
		assertThat(countedToday(user)).isZero();
	}

	@Test
	@DisplayName("남의 job은 NOT_FOUND")
	void get_otherUsersJob_notFound() {
		User a = newUser();
		User b = newUser();
		Long jobId = extractionService.accept(a.getId(), jpeg());

		assertThatThrownBy(() -> extractionService.get(b.getId(), jobId))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).errorCode())
				.isEqualTo(ErrorCode.NOT_FOUND);
		assertThat(extractionService.get(a.getId(), jobId).status()).isEqualTo(JobStatus.QUEUED);
	}

	@Test
	@DisplayName("조회 응답의 confirmed는 저장 여부를 알려 준다: FAILED든 DONE이든 저장 전 false, 저장 후 true")
	void get_confirmedFlag() {
		User user = newUser();
		Instant now = Instant.now(clock);
		ExtractionJob failed = ExtractionJob.queue(user, now);
		failed.fail("AI_TIMEOUT", false, now);
		ExtractionJob done = ExtractionJob.queue(user, now);
		done.complete("{}", now);
		jobRepository.saveAll(List.of(failed, done));

		assertThat(extractionService.get(user.getId(), failed.getId()).confirmed()).isFalse();
		assertThat(extractionService.get(user.getId(), done.getId()).confirmed()).isFalse();

		failed.markConfirmed(now);
		done.markConfirmed(now);
		jobRepository.saveAll(List.of(failed, done));

		var failedView = extractionService.get(user.getId(), failed.getId());
		assertThat(failedView.status()).isEqualTo(JobStatus.FAILED);
		assertThat(failedView.confirmed()).isTrue();
		var doneView = extractionService.get(user.getId(), done.getId());
		assertThat(doneView.status()).isEqualTo(JobStatus.DONE);
		assertThat(doneView.confirmed()).isTrue();
		assertThat(doneView.result()).isNull();
	}
}
