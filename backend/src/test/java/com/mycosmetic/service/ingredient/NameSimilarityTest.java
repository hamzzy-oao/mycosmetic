package com.mycosmetic.service.ingredient;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class NameSimilarityTest {

	@Test
	@DisplayName("같으면 1.0, 8글자 중 1글자 다르면 0.875")
	void sameAndOneOfEight() {
		assertThat(NameSimilarity.of("나이아신아마이드", "나이아신아마이드")).isEqualTo(1.0);
		assertThat(NameSimilarity.of("나이아신아마이드", "나이아신아마이트")).isEqualTo(0.875);
	}

	@Test
	@DisplayName("길이가 다르면 긴 쪽 길이로 나눈다")
	void dividesByLongerLength() {
		// 10글자 중 2글자 다름 = 0.8, 19글자 중 4글자 다름 ≈ 0.789
		assertThat(NameSimilarity.of("소듐하이알루로네이트", "소듐하이알루로나이드")).isCloseTo(0.8, within(1e-9));
		assertThat(NameSimilarity.of("다이포타슘글리시리제이트추출물에센스액", "다이포타슘글리시리제이트추출물오일크림"))
				.isCloseTo(1 - 4 / 19.0, within(1e-9));
		assertThat(NameSimilarity.of("글리세린", "글리세")).isEqualTo(0.75);
	}

	@Test
	@DisplayName("둘 다 빈 문자열이면 1.0, 한쪽만 비면 0.0")
	void empty() {
		assertThat(NameSimilarity.of("", "")).isEqualTo(1.0);
		assertThat(NameSimilarity.of("", "정제수")).isEqualTo(0.0);
	}
}
