package com.mycosmetic.client.ai;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowableOfType;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Map;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.anthropic.client.AnthropicClient;
import com.anthropic.client.okhttp.AnthropicOkHttpClient;
import com.jayway.jsonpath.DocumentContext;
import com.jayway.jsonpath.JsonPath;
import com.mycosmetic.client.ai.ExtractionClientException.Reason;
import com.mycosmetic.config.AiProperties;

import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;

/**
 * 실제 Claude 대신 MockWebServer가 저장해 둔 응답 JSON을 돌려준다.
 * 주의: src/test/resources/claude/*.json은 손으로 만든 응답이다. 실제 API 샘플로 바꿔야 한다.
 */
class ClaudeIngredientExtractionClientTest {

	MockWebServer server;
	AnthropicClient anthropic;
	ClaudeIngredientExtractionClient client;

	@BeforeEach
	void setUp() throws IOException {
		server = new MockWebServer();
		server.start();
		anthropic = AnthropicOkHttpClient.builder()
				.apiKey("test")
				.baseUrl(server.url("/").toString())
				.timeout(Duration.ofSeconds(1))
				.maxRetries(0)
				.build();
		client = new ClaudeIngredientExtractionClient(anthropic,
				new AiProperties("claude-sonnet-5-5", 16000L, Duration.ofSeconds(1), "v1"));
	}

	@AfterEach
	void tearDown() throws IOException {
		anthropic.close();
		server.shutdown();
	}

	@Test
	@DisplayName("이미지를 base64 JPEG 블록으로, 구조화 출력 스키마와 함께 보낸다")
	void sendsImageBlockAndSchema() throws Exception {
		server.enqueue(json("claude/extraction-response.json"));

		client.extract(new byte[] {1, 2, 3}, "image/jpeg");

		DocumentContext body = JsonPath.parse(server.takeRequest(1, TimeUnit.SECONDS).getBody().readUtf8());
		assertThat(body.read("$.model", String.class)).isEqualTo("claude-sonnet-5-5");
		assertThat(body.read("$.max_tokens", Long.class)).isEqualTo(16000L);
		assertThat(body.read("$.messages[0].content[0].type", String.class)).isEqualTo("image");
		assertThat(body.read("$.messages[0].content[0].source.media_type", String.class)).isEqualTo("image/jpeg");
		assertThat(body.read("$.messages[0].content[0].source.data", String.class)).isEqualTo("AQID");
		assertThat(body.read("$.messages[0].content[1].type", String.class)).isEqualTo("text");
		assertThat(body.read("$.output_config.format.type", String.class)).isEqualTo("json_schema");
		assertThat(body.read("$.fallbacks", String.class)).isEqualTo("default");
		Map<String, Object> root = body.read("$");
		assertThat(root).doesNotContainKeys("tool_choice", "thinking");
	}

	@Test
	@DisplayName("요청에 서버 측 fallback 베타 헤더를 넣는다")
	void sendsFallbackBetaHeader() throws Exception {
		server.enqueue(json("claude/extraction-response.json"));

		client.extract(new byte[] {1, 2, 3}, "image/png");

		var request = server.takeRequest(1, TimeUnit.SECONDS);
		assertThat(request.getHeader("anthropic-beta")).contains("server-side-fallback-2026-07-01");
		assertThat(JsonPath.parse(request.getBody().readUtf8())
				.read("$.messages[0].content[0].source.media_type", String.class)).isEqualTo("image/png");
	}

	@Test
	@DisplayName("응답 JSON을 ExtractionResult로 읽는다")
	void parsesResult() {
		server.enqueue(json("claude/extraction-response.json"));

		ExtractionResult result = client.extract(new byte[] {1, 2, 3}, "image/jpeg");

		assertThat(result.ingredientSectionFound()).isTrue();
		assertThat(result.ingredients()).containsExactly("정제수", "글리세린", "나이아신아마이드", "부틸렌글라이콜", "판테놀");
		assertThat(result.unreadableParts()).isEmpty();
	}

	@Test
	@DisplayName("429는 RATE_LIMITED, 재시도 가능")
	void rateLimited() {
		server.enqueue(error(429, "rate_limit_error"));

		ExtractionClientException e = extractFails();

		assertThat(e.reason()).isEqualTo(Reason.RATE_LIMITED);
		assertThat(e.retryable()).isTrue();
	}

	@Test
	@DisplayName("500·529는 SERVER_ERROR, 재시도 가능")
	void serverErrors() {
		server.enqueue(error(500, "api_error"));
		ExtractionClientException e500 = extractFails();
		server.enqueue(error(529, "overloaded_error"));
		ExtractionClientException e529 = extractFails();

		assertThat(e500.reason()).isEqualTo(Reason.SERVER_ERROR);
		assertThat(e500.retryable()).isTrue();
		assertThat(e529.reason()).isEqualTo(Reason.SERVER_ERROR);
		assertThat(e529.retryable()).isTrue();
	}

	@Test
	@DisplayName("응답이 타임아웃(테스트용 1초)을 넘으면 TIMEOUT")
	void timeout() {
		server.enqueue(json("claude/extraction-response.json").setHeadersDelay(3, TimeUnit.SECONDS));

		ExtractionClientException e = extractFails();

		assertThat(e.reason()).isEqualTo(Reason.TIMEOUT);
		assertThat(e.retryable()).isTrue();
	}

	@Test
	@DisplayName("JSON이 깨진 응답은 INVALID_RESPONSE, 재시도 가능")
	void invalidJson() {
		server.enqueue(json("claude/invalid-json-response.json"));

		ExtractionClientException e = extractFails();

		assertThat(e.reason()).isEqualTo(Reason.INVALID_RESPONSE);
		assertThat(e.retryable()).isTrue();
	}

	@Test
	@DisplayName("stop_reason=refusal이면 REFUSED, 재시도 불가")
	void refusal() {
		server.enqueue(json("claude/refusal-response.json"));

		ExtractionClientException e = extractFails();

		assertThat(e.reason()).isEqualTo(Reason.REFUSED);
		assertThat(e.retryable()).isFalse();
	}

	@Test
	@DisplayName("400은 BAD_REQUEST, 재시도 불가")
	void badRequest() {
		server.enqueue(error(400, "invalid_request_error"));

		ExtractionClientException e = extractFails();

		assertThat(e.reason()).isEqualTo(Reason.BAD_REQUEST);
		assertThat(e.retryable()).isFalse();
	}

	@Test
	@DisplayName("400이 아닌 4xx(401·403·404·413)는 BAD_REQUEST가 아니라 CLIENT_CONFIG, 재시도 불가")
	void otherClientErrors() {
		int[] statuses = {401, 403, 404, 413};
		String[] types = {"authentication_error", "permission_error", "not_found_error", "request_too_large"};
		for (int i = 0; i < statuses.length; i++) {
			server.enqueue(error(statuses[i], types[i]));

			ExtractionClientException e = extractFails();

			assertThat(e.reason()).as("HTTP %d", statuses[i]).isEqualTo(Reason.CLIENT_CONFIG);
			assertThat(e.retryable()).as("HTTP %d", statuses[i]).isFalse();
		}
	}

	private ExtractionClientException extractFails() {
		ExtractionClientException e = catchThrowableOfType(ExtractionClientException.class,
				() -> client.extract(new byte[] {1, 2, 3}, "image/jpeg"));
		assertThat(e).as("ExtractionClientException이 나야 한다").isNotNull();
		return e;
	}

	private static MockResponse json(String resource) {
		try (InputStream in = ClaudeIngredientExtractionClientTest.class.getClassLoader().getResourceAsStream(resource)) {
			return new MockResponse()
					.setHeader("Content-Type", "application/json")
					.setBody(new String(in.readAllBytes(), StandardCharsets.UTF_8));
		} catch (IOException e) {
			throw new IllegalStateException(e);
		}
	}

	private static MockResponse error(int status, String type) {
		return new MockResponse()
				.setResponseCode(status)
				.setHeader("Content-Type", "application/json")
				.setBody("{\"type\":\"error\",\"error\":{\"type\":\"" + type + "\",\"message\":\"test\"}}");
	}
}
