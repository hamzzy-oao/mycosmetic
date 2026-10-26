package com.mycosmetic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.EnumSet;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.mycosmetic.entity.ExtractionJob;
import com.mycosmetic.entity.JobStatus;
import com.mycosmetic.entity.User;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.repository.ExtractionJobRepository;
import com.mycosmetic.repository.ProductRepository;
import com.mycosmetic.service.extraction.ExtractionService;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.TestAuthHelper;

import tools.jackson.databind.json.JsonMapper;

/**
 * POST /api/extractions/manual — 사진 없이 직접 붙여넣기 시작. 보안 테스트 4종과 저장까지 이어지는 흐름.
 */
class ManualExtractionSecurityTest extends IntegrationTestSupport {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TestAuthHelper auth;

	@Autowired
	ExtractionJobRepository jobRepository;

	@Autowired
	ProductRepository productRepository;

	@Autowired
	JsonMapper jsonMapper;

	private User newUser() {
		return auth.createUser(TestAuthHelper.uniqueEmail());
	}

	private long allJobs(User user) {
		return jobRepository.countByUserIdAndStatusIn(user.getId(), EnumSet.allOf(JobStatus.class));
	}

	private long countedJobs(User user) {
		return jobRepository.countByUserIdAndCountsTowardLimitTrueAndCreatedAtGreaterThanEqual(user.getId(),
				Instant.EPOCH);
	}

	private Long startManual(User user) throws Exception {
		String body = mockMvc.perform(post("/api/extractions/manual").with(csrf()).cookie(auth.accessTokenCookie(user)))
				.andExpect(status().isCreated())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		return jsonMapper.readTree(body).get("jobId").asLong();
	}

	@Test
	@DisplayName("로그인하지 않으면 401과 공통 오류 형식")
	void anonymous_returns401() throws Exception {
		mockMvc.perform(post("/api/extractions/manual").with(csrf()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").value(ErrorCode.UNAUTHORIZED.message()))
				.andExpect(jsonPath("$.traceId").value(matchesPattern("[0-9a-f]{8}")));
	}

	@Test
	@DisplayName("CSRF 토큰 없이 보내면 403이고 job을 만들지 않는다")
	void withoutCsrf_returns403() throws Exception {
		User user = newUser();

		mockMvc.perform(post("/api/extractions/manual").cookie(auth.accessTokenCookie(user)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CSRF_INVALID"));

		assertThat(allJobs(user)).isZero();
	}

	@Test
	@DisplayName("A가 만든 직접 입력 job은 B가 조회할 수 없다(404)")
	void otherUsersJob_returns404() throws Exception {
		User a = newUser();
		User b = newUser();
		Long jobId = startManual(a);

		mockMvc.perform(get("/api/extractions/{id}", jobId).cookie(auth.accessTokenCookie(b)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	@DisplayName("본인 요청은 201, 한도를 쓰지 않는 FAILED(MANUAL_INPUT) job")
	void own_returns201() throws Exception {
		User user = newUser();

		Long jobId = startManual(user);

		ExtractionJob job = jobRepository.findByIdAndUserId(jobId, user.getId()).orElseThrow();
		assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
		assertThat(job.getErrorCode()).isEqualTo(ExtractionService.MANUAL_INPUT);
		assertThat(job.isCountsTowardLimit()).isFalse();
		mockMvc.perform(get("/api/extractions/{id}", jobId).cookie(auth.accessTokenCookie(user)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("FAILED"))
				.andExpect(jsonPath("$.errorCode").value("MANUAL_INPUT"))
				.andExpect(jsonPath("$.confirmed").value(false));
	}

	@Test
	@DisplayName("직접 입력 job으로 붙여넣은 성분을 저장하면 201이고 하루 한도에 1회로 센다")
	void manualJob_confirm_countsOnce() throws Exception {
		User user = newUser();
		Long jobId = startManual(user);
		String barcode = "880" + UUID.randomUUID().toString().substring(0, 12);
		String body = """
				{"jobId": %d,
				 "newProduct": {"barcode": "%s", "brand": "브랜드", "name": "토너", "volume": null, "category": "SKINCARE"},
				 "items": [{"ingredientId": null, "rawText": "정제수"}, {"ingredientId": null, "rawText": "글리세린"}]}
				""".formatted(jobId, barcode);

		mockMvc.perform(post("/api/products/confirm").with(csrf()).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content(body))
				.andExpect(status().isCreated());

		assertThat(productRepository.findByBarcode(barcode)).isPresent();
		assertThat(countedJobs(user)).isEqualTo(1);
	}

	@Test
	@DisplayName("오늘 한도를 다 쓰면 429 EXTRACTION_DAILY_LIMIT")
	void dailyLimit_rejects() throws Exception {
		User user = newUser();
		for (int i = 0; i < ExtractionService.DAILY_LIMIT; i++) {
			ExtractionJob job = ExtractionJob.queue(user);
			job.complete("{}", Instant.now());
			jobRepository.save(job);
		}

		mockMvc.perform(post("/api/extractions/manual").with(csrf()).cookie(auth.accessTokenCookie(user)))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("EXTRACTION_DAILY_LIMIT"));
	}
}
