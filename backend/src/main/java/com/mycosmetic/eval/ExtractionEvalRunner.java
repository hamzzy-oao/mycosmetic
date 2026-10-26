package com.mycosmetic.eval;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.stream.Stream;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

import com.mycosmetic.client.ai.ExtractionClientException;
import com.mycosmetic.client.ai.ExtractionResult;
import com.mycosmetic.client.ai.IngredientExtractionClient;
import com.mycosmetic.service.ingredient.IngredientMatcher;

import tools.jackson.databind.json.JsonMapper;

/**
 * 추출 정확도 평가 실행기 (설계 8.1, eval 프로필에서만 동작).
 * 사진 폴더의 jpg·jpeg·png마다 성분 추출을 한 번 호출하고 {@code <결과 폴더>/<제품ID>.json}을 쓴다.
 * 제품 ID는 파일 이름에서 확장자를 뺀 부분이다. 지표 계산은 extraction-eval 스킬의 metrics.py가 한다.
 * items는 읽은 성분을 성분 사전으로 매칭한 결과다(사전 자동 확정률 계산용).
 * 실패한 사진은 재시도하지 않는다(평가는 1회 결과 그대로).
 *
 * <pre>./gradlew bootRun --args='--spring.profiles.active=eval --eval.photos=C:/mycosmetic-eval/photos'</pre>
 */
@Component
@Profile("eval")
public class ExtractionEvalRunner implements ApplicationRunner {

	private static final Logger log = LoggerFactory.getLogger(ExtractionEvalRunner.class);

	/** 스킬 1번의 결과 형식 */
	record EvalOutput(List<String> ingredients, List<EvalItem> items, String error) {
	}

	/** 사전 매칭 결과 한 줄 */
	record EvalItem(String rawText, String matchType) {
	}

	private final IngredientExtractionClient client;
	private final IngredientMatcher matcher;
	private final JsonMapper mapper = JsonMapper.builder().build();

	public ExtractionEvalRunner(IngredientExtractionClient client, IngredientMatcher matcher) {
		this.client = client;
		this.matcher = matcher;
	}

	@Override
	public void run(ApplicationArguments args) throws IOException {
		Path photos = Path.of(option(args, "eval.photos")
				.orElseThrow(() -> new IllegalArgumentException("--eval.photos=<사진 폴더>가 필요하다")));
		Path out = Path.of(option(args, "eval.out")
				.orElse("build/eval/" + LocalDate.now(ZoneId.of("Asia/Seoul"))));
		Files.createDirectories(out);

		List<Path> images;
		try (Stream<Path> files = Files.list(photos)) {
			images = files.filter(Files::isRegularFile)
					.filter(p -> mediaType(p).isPresent())
					.sorted()
					.toList();
		}

		int failed = 0;
		for (Path image : images) {
			EvalOutput output = extract(image);
			if (output.error() != null) {
				failed++;
			}
			mapper.writeValue(out.resolve(productId(image) + ".json").toFile(), output);
		}
		log.info("성분 추출 평가 완료. 처리={}장, 실패={}장, 결과 폴더={}", images.size(), failed, out.toAbsolutePath());
	}

	private EvalOutput extract(Path image) throws IOException {
		byte[] bytes = Files.readAllBytes(image);
		try {
			ExtractionResult result = client.extract(bytes, mediaType(image).orElseThrow());
			List<EvalItem> items = matcher.match(result.ingredients()).stream()
					.map(m -> new EvalItem(m.rawText(), m.type().name()))
					.toList();
			return new EvalOutput(result.ingredients(), items, null);
		} catch (ExtractionClientException e) {
			return new EvalOutput(List.of(), List.of(), e.reason().name());
		} catch (RuntimeException e) {
			log.warn("평가 중 예상하지 못한 오류. 제품={}", productId(image), e);
			return new EvalOutput(List.of(), List.of(), "UNEXPECTED");
		}
	}

	private static Optional<String> option(ApplicationArguments args, String name) {
		List<String> values = args.getOptionValues(name);
		return values == null || values.isEmpty() ? Optional.empty() : Optional.of(values.get(0));
	}

	private static Optional<String> mediaType(Path file) {
		String name = file.getFileName().toString().toLowerCase(Locale.ROOT);
		if (name.endsWith(".jpg") || name.endsWith(".jpeg")) {
			return Optional.of("image/jpeg");
		}
		if (name.endsWith(".png")) {
			return Optional.of("image/png");
		}
		return Optional.empty();
	}

	private static String productId(Path file) {
		String name = file.getFileName().toString();
		int dot = name.lastIndexOf('.');
		return dot < 0 ? name : name.substring(0, dot);
	}
}
