package com.mycosmetic.eval;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.IntStream;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.boot.DefaultApplicationArguments;

import com.mycosmetic.client.ai.ExtractionClientException;
import com.mycosmetic.client.ai.ExtractionClientException.Reason;
import com.mycosmetic.client.ai.ExtractionResult;
import com.mycosmetic.client.ai.IngredientExtractionClient;
import com.mycosmetic.entity.MatchType;
import com.mycosmetic.service.ingredient.IngredientMatcher;
import com.mycosmetic.service.ingredient.MatchResult;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

class ExtractionEvalRunnerTest {

	@TempDir
	Path photos;

	@TempDir
	Path out;

	final JsonMapper mapper = JsonMapper.builder().build();
	final List<String> receivedMediaTypes = new ArrayList<>();
	final IngredientMatcher matcher = mock(IngredientMatcher.class);

	@BeforeEach
	void setUp() throws Exception {
		// 정제수는 EXACT, 나머지는 UNMATCHED로 매칭하는 가짜 matcher
		when(matcher.match(anyList())).thenAnswer(invocation -> {
			List<String> names = invocation.getArgument(0);
			return IntStream.range(0, names.size())
					.mapToObj(i -> "정제수".equals(names.get(i))
							? new MatchResult(i + 1, names.get(i), MatchType.EXACT, 1L, "정제수", List.of())
							: new MatchResult(i + 1, names.get(i), MatchType.UNMATCHED, null, null, List.of()))
					.toList();
		});
		Files.write(photos.resolve("a.jpg"), new byte[] {1, 2, 3});
		Files.write(photos.resolve("b.png"), new byte[] {4, 5, 6});
	}

	@Test
	@DisplayName("사진마다 <id>.json을 스킬 형식으로 쓴다")
	void writesResultPerPhoto() throws Exception {
		run(fakeClient(false));

		JsonNode a = mapper.readTree(out.resolve("a.json").toFile());
		assertThat(a.get("ingredients").valueStream().map(JsonNode::asString).toList())
				.containsExactly("정제수", "글리세린");
		assertThat(a.get("items").isArray()).isTrue();
		assertThat(a.get("error").isNull()).isTrue();
		assertThat(out.resolve("b.json")).exists();
	}

	@Test
	@DisplayName("items에 rawText와 matchType을 쓴다")
	void writesMatchItems() throws Exception {
		run(fakeClient(false));

		JsonNode items = mapper.readTree(out.resolve("a.json").toFile()).get("items");
		assertThat(items.size()).isEqualTo(2);
		assertThat(items.get(0).get("rawText").asString()).isEqualTo("정제수");
		assertThat(items.get(0).get("matchType").asString()).isEqualTo("EXACT");
		assertThat(items.get(1).get("rawText").asString()).isEqualTo("글리세린");
		assertThat(items.get(1).get("matchType").asString()).isEqualTo("UNMATCHED");
		assertThat(items.get(0).propertyNames()).containsExactlyInAnyOrder("rawText", "matchType");
	}

	@Test
	@DisplayName("PNG는 image/png로 보낸다")
	void pngIsSentAsPng() throws Exception {
		run(fakeClient(false));

		assertThat(receivedMediaTypes).containsExactly("image/jpeg", "image/png");
	}

	@Test
	@DisplayName("한 장이 실패해도 나머지를 계속하고 error에 이유를 쓴다")
	void failureIsRecordedAndOthersContinue() throws Exception {
		run(fakeClient(true));

		JsonNode a = mapper.readTree(out.resolve("a.json").toFile());
		JsonNode b = mapper.readTree(out.resolve("b.json").toFile());
		assertThat(a.get("error").isNull()).isTrue();
		assertThat(a.get("ingredients").size()).isEqualTo(2);
		assertThat(b.get("ingredients").isEmpty()).isTrue();
		assertThat(b.get("items").isEmpty()).isTrue();
		assertThat(b.get("error").asString()).isEqualTo("RATE_LIMITED");
	}

	@Test
	@DisplayName("jpg·jpeg·png 외 파일은 건너뛴다")
	void skipsNonImageFiles() throws Exception {
		Files.writeString(photos.resolve("notes.txt"), "메모");
		Files.write(photos.resolve("c.JPEG"), new byte[] {7});

		run(fakeClient(false));

		assertThat(out.resolve("notes.json")).doesNotExist();
		assertThat(out.resolve("c.json")).exists();
		assertThat(receivedMediaTypes).hasSize(3);
	}

	private void run(IngredientExtractionClient client) throws Exception {
		new ExtractionEvalRunner(client, matcher).run(new DefaultApplicationArguments(
				"--eval.photos=" + photos, "--eval.out=" + out));
	}

	/** PNG에서 실패하게 할 수 있는 가짜 client */
	private IngredientExtractionClient fakeClient(boolean failOnPng) {
		return (image, mediaType) -> {
			receivedMediaTypes.add(mediaType);
			if (failOnPng && "image/png".equals(mediaType)) {
				throw new ExtractionClientException(Reason.RATE_LIMITED, "테스트");
			}
			return new ExtractionResult(true, List.of("정제수", "글리세린"), List.of());
		};
	}
}
