package com.mycosmetic.service.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Duration;
import java.time.Instant;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.mycosmetic.client.ai.ExtractionClientException;
import com.mycosmetic.client.ai.ExtractionClientException.Reason;
import com.mycosmetic.client.ai.IngredientExtractionClient;
import com.mycosmetic.entity.ExtractionJob;
import com.mycosmetic.entity.JobStatus;
import com.mycosmetic.entity.User;
import com.mycosmetic.repository.ExtractionJobRepository;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.TestAuthHelper;
import com.mycosmetic.support.TestImages;

/** Claude 400(BAD_REQUEST) 실패는 사용자가 되풀이해 일으킬 수 있어 하루 한도에 센다(보안 리뷰) */
class ExtractionBadRequestLimitTest extends IntegrationTestSupport {

	@Autowired
	ExtractionService extractionService;

	@Autowired
	ExtractionJobRepository jobRepository;

	@Autowired
	TestAuthHelper auth;

	@Autowired
	MockMvc mockMvc;

	@MockitoBean
	IngredientExtractionClient client;

	@Test
	@DisplayName("BAD_REQUEST로 실패한 job 20개 뒤 21번째 접수는 429 EXTRACTION_DAILY_LIMIT")
	void twentyBadRequests_then21stRejected() throws Exception {
		when(client.extract(any(), any())).thenThrow(new ExtractionClientException(Reason.BAD_REQUEST, "400"));
		User user = auth.createUser(TestAuthHelper.uniqueEmail());
		MockMultipartFile image = new MockMultipartFile("image", "label.jpg", "image/jpeg", TestImages.jpeg());

		for (int i = 0; i < 20; i++) {
			Long jobId = extractionService.accept(user.getId(), image);
			ExtractionJob job = awaitFinished(jobId, user);
			assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
			assertThat(job.isCountsTowardLimit()).isTrue();
		}

		mockMvc.perform(multipart("/api/extractions").file(image).with(csrf()).cookie(auth.accessTokenCookie(user)))
				.andExpect(status().isTooManyRequests())
				.andExpect(jsonPath("$.code").value("EXTRACTION_DAILY_LIMIT"));
	}

	private ExtractionJob awaitFinished(Long jobId, User user) throws InterruptedException {
		Instant deadline = Instant.now().plus(Duration.ofSeconds(10));
		ExtractionJob job = jobRepository.findByIdAndUserId(jobId, user.getId()).orElseThrow();
		while (!job.isFinished() && Instant.now().isBefore(deadline)) {
			Thread.sleep(20);
			job = jobRepository.findByIdAndUserId(jobId, user.getId()).orElseThrow();
		}
		return job;
	}
}
