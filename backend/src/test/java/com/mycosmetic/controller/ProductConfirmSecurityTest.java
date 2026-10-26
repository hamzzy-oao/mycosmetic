package com.mycosmetic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.mycosmetic.entity.ExtractionJob;
import com.mycosmetic.entity.User;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.repository.ExtractionJobRepository;
import com.mycosmetic.repository.ProductRepository;
import com.mycosmetic.repository.ShelfItemRepository;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.TestAuthHelper;

/** POST /api/products/confirm — 보안 테스트 4종 */
class ProductConfirmSecurityTest extends IntegrationTestSupport {

	static final String RESULT_JSON = "{\"ingredientSectionFound\":true,\"unreadableParts\":[],\"warning\":null,\"items\":[]}";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TestAuthHelper auth;

	@Autowired
	ExtractionJobRepository jobRepository;

	@Autowired
	ProductRepository productRepository;

	@Autowired
	ShelfItemRepository shelfItemRepository;

	private User newUser() {
		return auth.createUser(TestAuthHelper.uniqueEmail());
	}

	private ExtractionJob doneJob(User user) {
		ExtractionJob job = ExtractionJob.queue(user);
		job.complete(RESULT_JSON, Instant.now());
		return jobRepository.save(job);
	}

	/** 화면이 보내는 모양 그대로: newProduct(volume null), items [{ingredientId: null, rawText}] */
	private static String body(Long jobId, String barcode) {
		return """
				{"jobId": %d,
				 "newProduct": {"barcode": "%s", "brand": "브랜드", "name": "토너", "volume": null, "category": "SKINCARE"},
				 "items": [{"ingredientId": null, "rawText": "정제수"}, {"ingredientId": null, "rawText": "글리세린"}]}
				""".formatted(jobId, barcode);
	}

	private static String newBarcode() {
		return "880" + UUID.randomUUID().toString().substring(0, 12);
	}

	@Test
	@DisplayName("로그인하지 않으면 401과 공통 오류 형식")
	void anonymous_returns401() throws Exception {
		mockMvc.perform(post("/api/products/confirm").with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(body(1L, newBarcode())))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").value(ErrorCode.UNAUTHORIZED.message()))
				.andExpect(jsonPath("$.traceId").value(matchesPattern("[0-9a-f]{8}")));
	}

	@Test
	@DisplayName("CSRF 토큰 없이 보내면 403이고 아무것도 저장하지 않는다")
	void withoutCsrf_returns403() throws Exception {
		User user = newUser();
		ExtractionJob job = doneJob(user);
		String barcode = newBarcode();

		mockMvc.perform(post("/api/products/confirm").cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content(body(job.getId(), barcode)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CSRF_INVALID"));

		assertThat(productRepository.findByBarcode(barcode)).isEmpty();
		assertThat(jobRepository.findById(job.getId()).orElseThrow().getResultJson()).isNotNull();
	}

	@Test
	@DisplayName("B가 A의 jobId로 확인하면 404이고 A의 job 결과는 그대로")
	void otherUsersJob_returns404() throws Exception {
		User a = newUser();
		User b = newUser();
		ExtractionJob job = doneJob(a);
		String barcode = newBarcode();

		mockMvc.perform(post("/api/products/confirm").with(csrf()).cookie(auth.accessTokenCookie(b))
				.contentType(MediaType.APPLICATION_JSON).content(body(job.getId(), barcode)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));

		ExtractionJob after = jobRepository.findById(job.getId()).orElseThrow();
		assertThat(after.getResultJson()).isNotNull();
		assertThat(after.isConfirmed()).isFalse();
		assertThat(productRepository.findByBarcode(barcode)).isEmpty();
		assertThat(shelfItemRepository.findByUserIdOrderByUpdatedAtDesc(b.getId())).isEmpty();
	}

	@Test
	@DisplayName("본인 요청은 201과 ConfirmResponse")
	void own_returns201() throws Exception {
		User user = newUser();
		ExtractionJob job = doneJob(user);
		String barcode = newBarcode();

		mockMvc.perform(post("/api/products/confirm").with(csrf()).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content(body(job.getId(), barcode)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.productId").isNumber())
				.andExpect(jsonPath("$.ingredientListId").isNumber())
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.shelfItemId").isNumber());

		assertThat(productRepository.findByBarcode(barcode)).isPresent();
		assertThat(shelfItemRepository.findByUserIdOrderByUpdatedAtDesc(user.getId())).hasSize(1);
		assertThat(jobRepository.findById(job.getId()).orElseThrow().getResultJson()).isNull();
	}

	@Test
	@DisplayName("아직 읽는 중인 job이면 409 EXTRACTION_NOT_READY")
	void queuedJob_returns409() throws Exception {
		User user = newUser();
		ExtractionJob job = jobRepository.save(ExtractionJob.queue(user));

		mockMvc.perform(post("/api/products/confirm").with(csrf()).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content(body(job.getId(), newBarcode())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("EXTRACTION_NOT_READY"))
				.andExpect(jsonPath("$.message").value(ErrorCode.EXTRACTION_NOT_READY.message()));
	}

	@Test
	@DisplayName("성분 목록이 비었거나 newProduct 이름이 없으면 400 INVALID_REQUEST")
	void invalidBody_returns400() throws Exception {
		User user = newUser();
		ExtractionJob job = doneJob(user);

		mockMvc.perform(post("/api/products/confirm").with(csrf()).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"jobId\": %d, \"productId\": 1, \"items\": []}".formatted(job.getId())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		mockMvc.perform(post("/api/products/confirm").with(csrf()).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON)
				.content("""
						{"jobId": %d, "newProduct": {"brand": "b", "name": " ", "category": "SKINCARE"},
						 "items": [{"rawText": "정제수"}]}
						""".formatted(job.getId())))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		assertThat(jobRepository.findById(job.getId()).orElseThrow().getResultJson()).isNotNull();
	}
}
