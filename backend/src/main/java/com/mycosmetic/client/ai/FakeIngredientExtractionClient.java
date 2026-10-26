package com.mycosmetic.client.ai;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import tools.jackson.databind.json.JsonMapper;

/**
 * 실제 Claude 대신 저장해 둔 결과를 돌려준다(테스트·E2E용, app.extraction.client=fake).
 * 이미지 첫 바이트가 0x00이면 전성분 영역이 없는 사진으로 보고 ingredientSectionFound=false를 돌려준다.
 */
@Component
@ConditionalOnProperty(name = "app.extraction.client", havingValue = "fake")
public class FakeIngredientExtractionClient implements IngredientExtractionClient {

	private static final String SAMPLE = "fake/extraction-sample.json";

	private final ExtractionResult sample;

	public FakeIngredientExtractionClient() {
		try (InputStream in = getClass().getClassLoader().getResourceAsStream(SAMPLE)) {
			if (in == null) {
				throw new IllegalStateException("가짜 추출 결과 파일이 없다: " + SAMPLE);
			}
			this.sample = JsonMapper.builder().build().readValue(in, ExtractionResult.class);
		} catch (IOException e) {
			throw new IllegalStateException("가짜 추출 결과 파일을 읽을 수 없다: " + SAMPLE, e);
		}
	}

	@Override
	public ExtractionResult extract(byte[] image, String mediaType) {
		if (image.length > 0 && image[0] == 0x00) {
			return new ExtractionResult(false, List.of(), List.of());
		}
		return sample;
	}
}
