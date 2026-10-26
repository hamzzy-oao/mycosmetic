package com.mycosmetic.service.extraction;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;

import com.mycosmetic.client.ai.ExtractionClientException;
import com.mycosmetic.client.ai.ExtractionClientException.Reason;
import com.mycosmetic.client.ai.ExtractionResult;
import com.mycosmetic.client.ai.IngredientExtractionClient;
import com.mycosmetic.entity.ExtractionJob;
import com.mycosmetic.entity.JobStatus;
import com.mycosmetic.entity.MatchType;
import com.mycosmetic.entity.User;
import com.mycosmetic.repository.ExtractionJobRepository;
import com.mycosmetic.service.ingredient.IngredientMatcher;
import com.mycosmetic.service.ingredient.MatchResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/** 설계 6.2의 실패 처리. 재시도 대기는 0으로 둔다 */
class ExtractionWorkerTest {

	static final Long JOB_ID = 1L;
	static final byte[] IMAGE = { (byte) 0xFF, (byte) 0xD8, (byte) 0xFF };
	static final Instant NOW = Instant.parse("2026-10-22T03:00:00Z");

	IngredientExtractionClient client = mock(IngredientExtractionClient.class);
	IngredientMatcher matcher = mock(IngredientMatcher.class);
	ExtractionJobRepository repository = mock(ExtractionJobRepository.class);
	JsonMapper jsonMapper = JsonMapper.builder().build();
	ExtractionJob job;
	ExtractionWorker worker;

	@BeforeEach
	void setUp() {
		job = ExtractionJob.queue(User.ofEmail("a@test.com", "hash", "테스터"), NOW);
		when(repository.findById(JOB_ID)).thenReturn(Optional.of(job));
		// 트랜잭션 관리자는 가짜: 엔티티 변경을 그대로 확인한다
		TransactionTemplate tx = new TransactionTemplate(mock(PlatformTransactionManager.class));
		worker = new ExtractionWorker(client, matcher, repository, tx, jsonMapper,
				Clock.fixed(NOW, ZoneOffset.UTC), Duration.ZERO);
	}

	private static ExtractionResult found(String... names) {
		return new ExtractionResult(true, List.of(names), List.of("3번째 줄 끝부분"));
	}

	private void matchAll(MatchType... types) {
		// 다시 스텁할 때 이전 답이 불리지 않도록 doAnswer를 쓴다
		doAnswer(invocation -> {
			List<String> names = invocation.getArgument(0);
			List<MatchResult> results = new ArrayList<>();
			for (int i = 0; i < names.size(); i++) {
				MatchType type = types[i];
				Long id = type == MatchType.EXACT ? 100L + i : null;
				results.add(new MatchResult(i + 1, names.get(i), type, id, id == null ? null : "표준" + i, List.of()));
			}
			return results;
		}).when(matcher).match(any());
	}

	private JsonNode result() {
		return jsonMapper.readTree(job.getResultJson());
	}

	private static MatchType[] types(int exact, int unmatched) {
		MatchType[] types = new MatchType[exact + unmatched];
		for (int i = 0; i < types.length; i++) {
			types[i] = i < exact ? MatchType.EXACT : MatchType.UNMATCHED;
		}
		return types;
	}

	private static String[] names(int count) {
		String[] names = new String[count];
		for (int i = 0; i < count; i++) {
			names[i] = "성분" + i;
		}
		return names;
	}

	@Test
	@DisplayName("성공하면 DONE이고 result_json에 매칭 결과")
	void success_savesDoneWithResult() {
		when(client.extract(IMAGE, "image/jpeg")).thenReturn(found("정제수", "글리세린"));
		matchAll(MatchType.EXACT, MatchType.UNMATCHED);

		worker.run(JOB_ID, IMAGE, "image/jpeg");

		assertThat(job.getStatus()).isEqualTo(JobStatus.DONE);
		assertThat(job.isCountsTowardLimit()).isTrue();
		assertThat(job.getFinishedAt()).isEqualTo(NOW);
		JsonNode result = result();
		assertThat(result.get("ingredientSectionFound").asBoolean()).isTrue();
		assertThat(result.get("unreadableParts").get(0).asString()).isEqualTo("3번째 줄 끝부분");
		assertThat(result.get("items")).hasSize(2);
		JsonNode first = result.get("items").get(0);
		assertThat(first.get("position").asInt()).isEqualTo(1);
		assertThat(first.get("rawText").asString()).isEqualTo("정제수");
		assertThat(first.get("type").asString()).isEqualTo("EXACT");
		assertThat(first.get("ingredientId").asLong()).isEqualTo(100L);
		assertThat(first.get("standardName").asString()).isEqualTo("표준0");
		assertThat(first.get("candidates").isArray()).isTrue();
		// 1/2 = 50% UNMATCHED
		assertThat(result.get("warning").asString()).isEqualTo("MANY_UNMATCHED");
	}

	@Test
	@DisplayName("타임아웃 한 번 뒤 성공하면 DONE, client 호출 2번")
	void timeoutThenSuccess_retriesOnce() {
		when(client.extract(IMAGE, "image/jpeg"))
				.thenThrow(new ExtractionClientException(Reason.TIMEOUT, "timeout"))
				.thenReturn(found("정제수"));
		matchAll(MatchType.EXACT);

		worker.run(JOB_ID, IMAGE, "image/jpeg");

		assertThat(job.getStatus()).isEqualTo(JobStatus.DONE);
		verify(client, times(2)).extract(IMAGE, "image/jpeg");
		assertThat(result().get("warning").isNull()).isTrue();
	}

	@Test
	@DisplayName("두 번 다 429면 FAILED AI_UNAVAILABLE, 한도 미차감")
	void rateLimitedTwice_failsUnavailable() {
		when(client.extract(IMAGE, "image/jpeg"))
				.thenThrow(new ExtractionClientException(Reason.RATE_LIMITED, "429"));

		worker.run(JOB_ID, IMAGE, "image/jpeg");

		assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
		assertThat(job.getErrorCode()).isEqualTo("AI_UNAVAILABLE");
		assertThat(job.isCountsTowardLimit()).isFalse();
		assertThat(job.getFinishedAt()).isEqualTo(NOW);
		verify(client, times(2)).extract(IMAGE, "image/jpeg");
	}

	@Test
	@DisplayName("5xx 뒤 타임아웃이면 마지막 이유로 FAILED AI_TIMEOUT")
	void serverErrorThenTimeout_failsTimeout() {
		when(client.extract(IMAGE, "image/jpeg"))
				.thenThrow(new ExtractionClientException(Reason.SERVER_ERROR, "500"))
				.thenThrow(new ExtractionClientException(Reason.TIMEOUT, "timeout"));

		worker.run(JOB_ID, IMAGE, "image/jpeg");

		assertThat(job.getErrorCode()).isEqualTo("AI_TIMEOUT");
		assertThat(job.isCountsTowardLimit()).isFalse();
	}

	@Test
	@DisplayName("JSON 오류 두 번이면 FAILED AI_INVALID_RESPONSE")
	void invalidJsonTwice_failsInvalidResponse() {
		when(client.extract(IMAGE, "image/jpeg"))
				.thenThrow(new ExtractionClientException(Reason.INVALID_RESPONSE, "json"));

		worker.run(JOB_ID, IMAGE, "image/jpeg");

		assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
		assertThat(job.getErrorCode()).isEqualTo("AI_INVALID_RESPONSE");
		assertThat(job.isCountsTowardLimit()).isFalse();
		verify(client, times(2)).extract(IMAGE, "image/jpeg");
	}

	@Test
	@DisplayName("거절은 재시도 없이 FAILED AI_REFUSED")
	void refusal_failsWithoutRetry() {
		when(client.extract(IMAGE, "image/jpeg"))
				.thenThrow(new ExtractionClientException(Reason.REFUSED, "refusal"));

		worker.run(JOB_ID, IMAGE, "image/jpeg");

		assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
		assertThat(job.getErrorCode()).isEqualTo("AI_REFUSED");
		assertThat(job.isCountsTowardLimit()).isFalse();
		verify(client, times(1)).extract(IMAGE, "image/jpeg");
	}

	@Test
	@DisplayName("Claude 400(BAD_REQUEST)은 재시도 없이 FAILED AI_UNAVAILABLE이고 한도에 센다")
	void badRequest_failsCountedWithoutRetry() {
		// 사용자가 같은 사진으로 되풀이해 일으킬 수 있으므로 한도에서 빼지 않는다
		when(client.extract(IMAGE, "image/jpeg"))
				.thenThrow(new ExtractionClientException(Reason.BAD_REQUEST, "400"));

		worker.run(JOB_ID, IMAGE, "image/jpeg");

		assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
		assertThat(job.getErrorCode()).isEqualTo("AI_UNAVAILABLE");
		assertThat(job.isCountsTowardLimit()).isTrue();
		verify(client, times(1)).extract(IMAGE, "image/jpeg");
	}

	@Test
	@DisplayName("Claude 401 등(CLIENT_CONFIG)은 재시도 없이 FAILED AI_UNAVAILABLE이고 한도에 세지 않는다")
	void clientConfig_failsNotCounted() {
		// API 키·권한·설정 문제는 사용자 탓이 아니다
		when(client.extract(IMAGE, "image/jpeg"))
				.thenThrow(new ExtractionClientException(Reason.CLIENT_CONFIG, "401"));

		worker.run(JOB_ID, IMAGE, "image/jpeg");

		assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
		assertThat(job.getErrorCode()).isEqualTo("AI_UNAVAILABLE");
		assertThat(job.isCountsTowardLimit()).isFalse();
		verify(client, times(1)).extract(IMAGE, "image/jpeg");
	}

	@Test
	@DisplayName("ingredientSectionFound=false면 DONE + NO_INGREDIENT_SECTION, 한도 차감")
	void noIngredientSection_doneWithWarning() {
		when(client.extract(IMAGE, "image/jpeg")).thenReturn(new ExtractionResult(false, List.of(), List.of()));
		when(matcher.match(List.of())).thenReturn(List.of());

		worker.run(JOB_ID, IMAGE, "image/jpeg");

		assertThat(job.getStatus()).isEqualTo(JobStatus.DONE);
		assertThat(job.isCountsTowardLimit()).isTrue();
		JsonNode result = result();
		assertThat(result.get("ingredientSectionFound").asBoolean()).isFalse();
		assertThat(result.get("warning").asString()).isEqualTo("NO_INGREDIENT_SECTION");
		assertThat(result.get("items")).isEmpty();
	}

	@Test
	@DisplayName("UNMATCHED 30%면 MANY_UNMATCHED, 29%면 경고 없음")
	void unmatchedThreshold() {
		when(client.extract(IMAGE, "image/jpeg")).thenReturn(found(names(10)));
		matchAll(types(7, 3));

		worker.run(JOB_ID, IMAGE, "image/jpeg");

		assertThat(result().get("warning").asString()).isEqualTo("MANY_UNMATCHED");
		assertThat(job.isCountsTowardLimit()).isTrue();

		ExtractionJob second = ExtractionJob.queue(User.ofEmail("b@test.com", "hash", "테스터"), NOW);
		when(repository.findById(2L)).thenReturn(Optional.of(second));
		when(client.extract(IMAGE, "image/jpeg")).thenReturn(found(names(100)));
		matchAll(types(71, 29));

		worker.run(2L, IMAGE, "image/jpeg");

		assertThat(jsonMapper.readTree(second.getResultJson()).get("warning").isNull()).isTrue();
	}

	@Test
	@DisplayName("이미 끝났거나 지워진 job이면 AI를 부르지 않는다")
	void finishedOrMissingJob_skipsClient() {
		job.fail("SERVER_RESTARTED", false, NOW);
		when(repository.findById(3L)).thenReturn(Optional.empty());

		worker.run(JOB_ID, IMAGE, "image/jpeg");
		worker.run(3L, IMAGE, "image/jpeg");

		verify(client, never()).extract(any(), any());
		assertThat(job.getErrorCode()).isEqualTo("SERVER_RESTARTED");
	}

	@Test
	@DisplayName("매칭 중 예상하지 못한 오류면 FAILED INTERNAL_ERROR, 한도 미차감")
	void unexpectedError_failsWithoutLimit() {
		when(client.extract(IMAGE, "image/jpeg")).thenReturn(found("정제수"));
		when(matcher.match(any())).thenThrow(new IllegalStateException("db down"));

		worker.run(JOB_ID, IMAGE, "image/jpeg");

		assertThat(job.getStatus()).isEqualTo(JobStatus.FAILED);
		assertThat(job.getErrorCode()).isEqualTo("INTERNAL_ERROR");
		assertThat(job.isCountsTowardLimit()).isFalse();
	}
}
