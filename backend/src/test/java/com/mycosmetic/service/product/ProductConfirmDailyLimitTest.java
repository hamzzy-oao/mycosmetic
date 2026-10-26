package com.mycosmetic.service.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.mycosmetic.dto.product.ConfirmRequest;
import com.mycosmetic.dto.product.ConfirmRequest.NewProduct;
import com.mycosmetic.dto.product.FinalItem;
import com.mycosmetic.entity.Category;
import com.mycosmetic.entity.ExtractionJob;
import com.mycosmetic.entity.User;
import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.repository.ExtractionJobRepository;
import com.mycosmetic.repository.ProductRepository;
import com.mycosmetic.service.extraction.ExtractionService;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.TestAuthHelper;

/** 직접 붙여넣기(한도에서 뺀 FAILED job) 저장은 하루 한도 1회 차감 — 2026-10-22 사용자 결정 A */
class ProductConfirmDailyLimitTest extends IntegrationTestSupport {

	@Autowired
	ProductConfirmService confirmService;

	@Autowired
	ExtractionJobRepository jobRepository;

	@Autowired
	ProductRepository productRepository;

	@Autowired
	TestAuthHelper auth;

	@Autowired
	Clock clock;

	private User newUser() {
		return auth.createUser(TestAuthHelper.uniqueEmail());
	}

	private ExtractionJob failedJob(User user) {
		ExtractionJob job = ExtractionJob.queue(user, Instant.now(clock));
		job.fail("AI_TIMEOUT", false, Instant.now(clock));
		return jobRepository.save(job);
	}

	private ExtractionJob doneJob(User user) {
		ExtractionJob job = ExtractionJob.queue(user, Instant.now(clock));
		job.complete("{\"items\":[]}", Instant.now(clock));
		return jobRepository.save(job);
	}

	private void seedCountedJobs(User user, int count) {
		List<ExtractionJob> jobs = new ArrayList<>();
		for (int i = 0; i < count; i++) {
			ExtractionJob job = ExtractionJob.queue(user, Instant.now(clock));
			job.complete("{}", Instant.now(clock));
			jobs.add(job);
		}
		jobRepository.saveAll(jobs);
	}

	private long usedToday(User user) {
		return jobRepository.countByUserIdAndCountsTowardLimitTrueAndCreatedAtGreaterThanEqual(user.getId(),
				ExtractionService.startOfToday(Instant.now(clock)));
	}

	private static ConfirmRequest pasteRequest(Long jobId, String barcode) {
		String unique = UUID.randomUUID().toString().substring(0, 12);
		return new ConfirmRequest(jobId, null,
				new NewProduct(barcode, "브랜드", "크림 " + unique, null, Category.SKINCARE),
				List.of(new FinalItem(null, "직접쓴성분" + unique)));
	}

	private static String newBarcode() {
		return "882" + UUID.randomUUID().toString().substring(0, 12);
	}

	@Test
	@DisplayName("FAILED job으로 저장하면 그 job을 하루 한도에 센다")
	void failedJobConfirm_countsTowardLimit() {
		User user = newUser();
		ExtractionJob job = failedJob(user);
		assertThat(usedToday(user)).isZero();

		confirmService.confirm(user.getId(), pasteRequest(job.getId(), newBarcode()));

		ExtractionJob saved = jobRepository.findById(job.getId()).orElseThrow();
		assertThat(saved.isCountsTowardLimit()).isTrue();
		assertThat(saved.isConfirmed()).isTrue();
		assertThat(usedToday(user)).isEqualTo(1);
	}

	@Test
	@DisplayName("오늘 20회를 다 쓴 상태에서 FAILED job 저장은 429 EXTRACTION_DAILY_LIMIT이고 아무것도 저장하지 않는다")
	void failedJobConfirm_atLimit_429AndNothingSaved() {
		User user = newUser();
		seedCountedJobs(user, 20);
		ExtractionJob job = failedJob(user);
		String barcode = newBarcode();

		assertThatThrownBy(() -> confirmService.confirm(user.getId(), pasteRequest(job.getId(), barcode)))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.EXTRACTION_DAILY_LIMIT);

		ExtractionJob after = jobRepository.findById(job.getId()).orElseThrow();
		assertThat(after.isCountsTowardLimit()).isFalse();
		assertThat(after.isConfirmed()).isFalse();
		assertThat(productRepository.findByBarcode(barcode)).isEmpty();
		assertThat(usedToday(user)).isEqualTo(20);
	}

	@Test
	@DisplayName("DONE job 저장은 이미 센 것이라 다시 세지 않는다(20회를 다 써도 저장된다)")
	void doneJobConfirm_doesNotDoubleCount() {
		User user = newUser();
		seedCountedJobs(user, 19);
		ExtractionJob job = doneJob(user);
		assertThat(usedToday(user)).isEqualTo(20);

		confirmService.confirm(user.getId(), pasteRequest(job.getId(), newBarcode()));

		assertThat(jobRepository.findById(job.getId()).orElseThrow().isConfirmed()).isTrue();
		assertThat(usedToday(user)).isEqualTo(20);
	}
}
