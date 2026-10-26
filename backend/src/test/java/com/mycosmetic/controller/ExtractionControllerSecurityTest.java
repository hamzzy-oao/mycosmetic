package com.mycosmetic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import com.mycosmetic.entity.ExtractionJob;
import com.mycosmetic.entity.JobStatus;
import com.mycosmetic.entity.User;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.repository.ExtractionJobRepository;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.TestAuthHelper;
import com.mycosmetic.support.TestImages;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * POST /api/extractions, GET /api/extractions/{jobId} — 보안 테스트 4종.
 * AI는 가짜 client(app.extraction.client=fake)가 저장해 둔 결과를 돌려준다.
 */
class ExtractionControllerSecurityTest extends IntegrationTestSupport {

	static final byte[] JPEG = TestImages.jpeg();

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TestAuthHelper auth;

	@Autowired
	ExtractionJobRepository jobRepository;

	@Autowired
	JsonMapper jsonMapper;

	private static MockMultipartFile image(byte[] bytes) {
		return new MockMultipartFile("image", "label.jpg", "image/jpeg", bytes);
	}

	private User newUser() {
		return auth.createUser(TestAuthHelper.uniqueEmail());
	}

	private long jobCount(User user) {
		return jobRepository.countByUserIdAndCountsTowardLimitTrueAndCreatedAtGreaterThanEqual(user.getId(),
				Instant.EPOCH);
	}

	private Long accept(User user) throws Exception {
		String body = mockMvc.perform(multipart("/api/extractions").file(image(JPEG)).with(csrf())
				.cookie(auth.accessTokenCookie(user)))
				.andExpect(status().isAccepted())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		return jsonMapper.readTree(body).get("jobId").asLong();
	}

	private JsonNode getJob(User user, Long jobId) throws Exception {
		String body = mockMvc.perform(get("/api/extractions/{id}", jobId).cookie(auth.accessTokenCookie(user)))
				.andExpect(status().isOk())
				.andReturn().getResponse().getContentAsString(StandardCharsets.UTF_8);
		return jsonMapper.readTree(body);
	}

	// ---- POST /api/extractions ----

	@Test
	@DisplayName("POST: 로그인하지 않으면 401과 공통 오류 형식")
	void post_anonymous_returns401() throws Exception {
		mockMvc.perform(multipart("/api/extractions").file(image(JPEG)).with(csrf()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").value(ErrorCode.UNAUTHORIZED.message()))
				.andExpect(jsonPath("$.traceId").value(matchesPattern("[0-9a-f]{8}")));
	}

	@Test
	@DisplayName("POST: CSRF 토큰 없이 보내면 403이고 job을 만들지 않는다")
	void post_withoutCsrf_returns403() throws Exception {
		User user = newUser();

		mockMvc.perform(multipart("/api/extractions").file(image(JPEG)).cookie(auth.accessTokenCookie(user)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CSRF_INVALID"));

		assertThat(jobCount(user)).isZero();
	}

	@Test
	@DisplayName("POST: 본인 요청은 202와 jobId")
	void post_own_returns202() throws Exception {
		User user = newUser();

		Long jobId = accept(user);

		assertThat(jobRepository.findByIdAndUserId(jobId, user.getId())).isPresent();
	}

	@Test
	@DisplayName("fakeJpeg_returns415")
	void fakeJpeg_returns415() throws Exception {
		// Review Focus 4: 확장자·Content-Type은 JPEG지만 내용은 PDF
		User user = newUser();
		byte[] pdf = "%PDF-1.7 fake".getBytes(StandardCharsets.US_ASCII);

		mockMvc.perform(multipart("/api/extractions").file(image(pdf)).with(csrf())
				.cookie(auth.accessTokenCookie(user)))
				.andExpect(status().isUnsupportedMediaType())
				.andExpect(jsonPath("$.code").value("UNSUPPORTED_IMAGE"))
				.andExpect(jsonPath("$.message").value(ErrorCode.UNSUPPORTED_IMAGE.message()));

		assertThat(jobCount(user)).isZero();
	}

	@Test
	@DisplayName("5MB 넘는 파일은 413 FILE_TOO_LARGE")
	void tooLarge_returns413() throws Exception {
		User user = newUser();
		byte[] big = new byte[5 * 1024 * 1024 + 1];
		System.arraycopy(JPEG, 0, big, 0, JPEG.length);

		mockMvc.perform(multipart("/api/extractions").file(image(big)).with(csrf())
				.cookie(auth.accessTokenCookie(user)))
				.andExpect(status().isContentTooLarge())
				.andExpect(jsonPath("$.code").value("FILE_TOO_LARGE"));

		assertThat(jobCount(user)).isZero();
	}

	@Test
	@DisplayName("POST: image 파트가 없으면 400")
	void post_missingImage_returns400() throws Exception {
		User user = newUser();

		mockMvc.perform(multipart("/api/extractions").with(csrf()).cookie(auth.accessTokenCookie(user)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	// ---- GET /api/extractions/{jobId} ----

	@Test
	@DisplayName("GET: 로그인하지 않으면 401")
	void get_anonymous_returns401() throws Exception {
		mockMvc.perform(get("/api/extractions/{id}", 1L))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.traceId").value(matchesPattern("[0-9a-f]{8}")));
	}

	@Test
	@DisplayName("GET: B가 A의 jobId를 조회하면 404이고 A의 job은 그대로")
	void get_otherUsersJob_returns404() throws Exception {
		User a = newUser();
		User b = newUser();
		ExtractionJob job = ExtractionJob.queue(a, Instant.now());
		job.complete("{\"ingredientSectionFound\":true,\"unreadableParts\":[],\"items\":[],\"warning\":null}",
				Instant.now());
		Long jobId = jobRepository.save(job).getId();

		mockMvc.perform(get("/api/extractions/{id}", jobId).cookie(auth.accessTokenCookie(b)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));

		ExtractionJob reloaded = jobRepository.findByIdAndUserId(jobId, a.getId()).orElseThrow();
		assertThat(reloaded.getStatus()).isEqualTo(JobStatus.DONE);
		assertThat(reloaded.getResultJson()).isNotNull();
	}

	@Test
	@DisplayName("GET: 본인 job은 접수부터 DONE까지 확인할 수 있다")
	void get_own_pollsUntilDone() throws Exception {
		User user = newUser();
		Long jobId = accept(user);

		JsonNode job = getJob(user, jobId);
		Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
		while (!"DONE".equals(job.get("status").asString()) && Instant.now().isBefore(deadline)) {
			assertThat(job.get("status").asString()).isIn("QUEUED", "RUNNING");
			Thread.sleep(100);
			job = getJob(user, jobId);
		}

		assertThat(job.get("jobId").asLong()).isEqualTo(jobId);
		assertThat(job.get("status").asString()).isEqualTo("DONE");
		assertThat(job.get("errorCode").isNull()).isTrue();
		assertThat(job.get("confirmed").asBoolean()).isFalse();
		JsonNode result = job.get("result");
		assertThat(result.get("ingredientSectionFound").asBoolean()).isTrue();
		assertThat(result.get("unreadableParts").isArray()).isTrue();
		assertThat(result.has("warning")).isTrue();
		// 가짜 client의 저장 결과: 성분 5개
		assertThat(result.get("items")).hasSize(5);
		JsonNode first = result.get("items").get(0);
		assertThat(first.get("position").asInt()).isEqualTo(1);
		assertThat(first.get("rawText").asString()).isEqualTo("정제수");
		assertThat(first.get("type").asString()).isIn("EXACT", "ALIAS", "FUZZY", "UNMATCHED");
		assertThat(first.has("ingredientId")).isTrue();
		assertThat(first.has("standardName")).isTrue();
		assertThat(first.get("candidates").isArray()).isTrue();
	}

	@Test
	@DisplayName("GET: 실패한 job은 errorCode를 돌려주고 result는 null")
	void get_failedJob_returnsErrorCode() throws Exception {
		User user = newUser();
		ExtractionJob job = ExtractionJob.queue(user, Instant.now());
		job.fail("AI_REFUSED", false, Instant.now());
		Long jobId = jobRepository.save(job).getId();

		JsonNode body = getJob(user, jobId);

		assertThat(body.get("status").asString()).isEqualTo("FAILED");
		assertThat(body.get("errorCode").asString()).isEqualTo("AI_REFUSED");
		assertThat(body.get("result").isNull()).isTrue();
	}
}
