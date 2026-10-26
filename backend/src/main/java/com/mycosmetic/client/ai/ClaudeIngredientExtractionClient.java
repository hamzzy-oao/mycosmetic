package com.mycosmetic.client.ai;

import java.io.IOException;
import java.io.InputStream;
import java.io.InterruptedIOException;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.List;
import java.util.Optional;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import com.anthropic.client.AnthropicClient;
import com.anthropic.core.JsonValue;
import com.anthropic.errors.AnthropicException;
import com.anthropic.errors.AnthropicIoException;
import com.anthropic.errors.AnthropicServiceException;
import com.anthropic.errors.BadRequestException;
import com.anthropic.errors.InternalServerException;
import com.anthropic.errors.RateLimitException;
import com.anthropic.models.messages.Base64ImageSource;
import com.anthropic.models.messages.ContentBlockParam;
import com.anthropic.models.messages.ImageBlockParam;
import com.anthropic.models.messages.MessageCreateParams;
import com.anthropic.models.messages.StopReason;
import com.anthropic.models.messages.StructuredContentBlock;
import com.anthropic.models.messages.StructuredMessage;
import com.anthropic.models.messages.StructuredMessageCreateParams;
import com.anthropic.models.messages.StructuredTextBlock;
import com.anthropic.models.messages.TextBlockParam;
import com.mycosmetic.client.ai.ExtractionClientException.Reason;
import com.mycosmetic.config.AiProperties;

/**
 * Claude 비전 모델로 전성분을 읽는다. 출력은 {@link ExtractionResult} JSON 스키마로 고정한다(구조화 출력).
 * thinking, tool_choice, assistant prefill은 보내지 않는다. 이미지 바이트와 base64는 로그에 남기지 않는다.
 */
@Component
@ConditionalOnProperty(name = "app.extraction.client", havingValue = "claude", matchIfMissing = true)
public class ClaudeIngredientExtractionClient implements IngredientExtractionClient {

	private static final Logger log = LoggerFactory.getLogger(ClaudeIngredientExtractionClient.class);

	/** 안전 분류기 거절 대비 서버 측 fallback (계획 "Claude 거절 대비") */
	static final String FALLBACK_BETA = "server-side-fallback-2026-07-01";

	private final AnthropicClient client;
	private final AiProperties properties;
	private final String prompt;

	public ClaudeIngredientExtractionClient(AnthropicClient client, AiProperties properties) {
		this.client = client;
		this.properties = properties;
		this.prompt = loadPrompt(properties.promptVersion());
	}

	@Override
	public ExtractionResult extract(byte[] image, String mediaType) {
		StructuredMessageCreateParams<ExtractionResult> params = MessageCreateParams.builder()
				.model(properties.model())
				.maxTokens(properties.maxTokens())
				.outputConfig(ExtractionResult.class)
				.putAdditionalHeader("anthropic-beta", FALLBACK_BETA)
				.putAdditionalBodyProperty("fallbacks", JsonValue.from("default"))
				.addUserMessageOfBlockParams(List.of(
						ContentBlockParam.ofImage(ImageBlockParam.builder()
								.source(Base64ImageSource.builder()
										.data(Base64.getEncoder().encodeToString(image))
										.mediaType(toMediaType(mediaType))
										.build())
								.build()),
						ContentBlockParam.ofText(TextBlockParam.builder().text(prompt).build())))
				.build();

		StructuredMessage<ExtractionResult> message;
		try {
			message = client.messages().create(params);
		} catch (AnthropicException e) {
			throw translate(e);
		}

		if (message.stopReason().filter(StopReason.REFUSAL::equals).isPresent()) {
			log.warn("Claude가 성분 추출을 거절했다. category={}",
					message.stopDetails().flatMap(d -> d.category()).map(Object::toString).orElse("none"));
			throw new ExtractionClientException(Reason.REFUSED, "Claude가 요청을 거절했다");
		}
		return readResult(message);
	}

	private static ExtractionResult readResult(StructuredMessage<ExtractionResult> message) {
		Optional<StructuredTextBlock<ExtractionResult>> textBlock = message.content().stream()
				.map(StructuredContentBlock::text)
				.flatMap(Optional::stream)
				.findFirst();
		if (textBlock.isEmpty()) {
			throw new ExtractionClientException(Reason.INVALID_RESPONSE, "응답에 텍스트 블록이 없다");
		}
		ExtractionResult result;
		try {
			result = textBlock.get().text();
		} catch (RuntimeException e) {
			// 구조화 출력 JSON을 ExtractionResult로 읽지 못함 (SDK가 던지는 예외 종류에 기대지 않는다)
			throw new ExtractionClientException(Reason.INVALID_RESPONSE, "응답 JSON을 읽을 수 없다", e);
		}
		if (result == null) {
			throw new ExtractionClientException(Reason.INVALID_RESPONSE, "응답 JSON이 비어 있다");
		}
		return result;
	}

	private static ExtractionClientException translate(AnthropicException e) {
		if (e instanceof RateLimitException) {
			return new ExtractionClientException(Reason.RATE_LIMITED, "Claude 요청 한도 초과(429)", e);
		}
		if (e instanceof InternalServerException) {
			return new ExtractionClientException(Reason.SERVER_ERROR, "Claude 서버 오류", e);
		}
		if (e instanceof BadRequestException) {
			return new ExtractionClientException(Reason.BAD_REQUEST, "Claude 요청 형식 오류(400)", e);
		}
		if (e instanceof AnthropicIoException) {
			if (isTimeout(e)) {
				return new ExtractionClientException(Reason.TIMEOUT, "Claude 응답 시간 초과", e);
			}
			return new ExtractionClientException(Reason.SERVER_ERROR, "Claude 연결 오류", e);
		}
		if (e instanceof AnthropicServiceException service) {
			if (service.statusCode() >= 500) {
				// 529 overloaded 등
				return new ExtractionClientException(Reason.SERVER_ERROR, "Claude 서버 오류(" + service.statusCode() + ")", e);
			}
			if (service.statusCode() == 400) {
				return new ExtractionClientException(Reason.BAD_REQUEST, "Claude 요청 형식 오류(400)", e);
			}
			// 401·403·404·413 등: API 키·권한·서버 설정 문제. 사용자 탓이 아니므로 한도에 세지 않는다
			return new ExtractionClientException(Reason.CLIENT_CONFIG, "Claude 요청 거부(" + service.statusCode() + ")", e);
		}
		// AnthropicInvalidDataException 등 응답을 해석하지 못한 경우
		return new ExtractionClientException(Reason.INVALID_RESPONSE, "Claude 응답을 해석할 수 없다", e);
	}

	private static boolean isTimeout(Throwable e) {
		for (Throwable t = e; t != null; t = t.getCause()) {
			if (t instanceof InterruptedIOException) { // SocketTimeoutException 포함
				return true;
			}
		}
		return false;
	}

	private static Base64ImageSource.MediaType toMediaType(String mediaType) {
		return switch (mediaType) {
			case "image/jpeg" -> Base64ImageSource.MediaType.IMAGE_JPEG;
			case "image/png" -> Base64ImageSource.MediaType.IMAGE_PNG;
			default -> throw new IllegalArgumentException("지원하지 않는 이미지 형식: " + mediaType);
		};
	}

	private static String loadPrompt(String version) {
		String path = "prompts/ingredient-extraction-" + version + ".txt";
		try (InputStream in = ClaudeIngredientExtractionClient.class.getClassLoader().getResourceAsStream(path)) {
			if (in == null) {
				throw new IllegalStateException("프롬프트 파일이 없다: " + path);
			}
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		} catch (IOException e) {
			throw new IllegalStateException("프롬프트 파일을 읽을 수 없다: " + path, e);
		}
	}
}
