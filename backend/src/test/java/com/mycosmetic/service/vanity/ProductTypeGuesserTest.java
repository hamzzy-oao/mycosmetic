package com.mycosmetic.service.vanity;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.mycosmetic.entity.MakeupType;
import com.mycosmetic.entity.StepType;

/** 제품 이름으로 단계·색조 종류 추정(퍼스널 탭 설계 3절) */
class ProductTypeGuesserTest {

	@ParameterizedTest(name = "{0} → {1}")
	@CsvSource({
			"수분 아이크림, EYE",
			"세라마이드 크림, CREAM",
			"약산성 토너, TONER",
			"그린티 스킨, TONER",
			"마일드 클렌징폼, CLEANSER",
			"딥 클렌저, CLEANSER",
			"선스틱 SPF50+, SUNSCREEN",
			"톤업 선크림, SUNSCREEN",
			"비타 세럼, SERUM",
			"시카 앰플, AMPOULE",
			"히알루론 에센스, ESSENCE",
			"데일리 로션, LOTION",
			"밸런싱 에멀전, LOTION",
			"수딩 마스크팩, MASK",
			"선 크림, SUNSCREEN",
			"스킨케어 크림, CREAM",
	})
	@DisplayName("스킨케어 단계: 긴 키워드 우선, 공백 무시")
	void step(String name, StepType expected) {
		assertThat(ProductTypeGuesser.guessStep(name)).isEqualTo(expected);
	}

	@Test
	@DisplayName("아이크림은 크림이 아니라 EYE")
	void eyeCream_isEye() {
		assertThat(ProductTypeGuesser.guessStep("○○ 아이크림")).isEqualTo(StepType.EYE);
	}

	@Test
	@DisplayName("모르는 이름이나 빈 이름은 null")
	void unknown_isNull() {
		assertThat(ProductTypeGuesser.guessStep("미스트 오일")).isNull();
		assertThat(ProductTypeGuesser.guessStep(null)).isNull();
		assertThat(ProductTypeGuesser.guessMakeup("")).isNull();
	}

	@ParameterizedTest(name = "{0} → {1}")
	@CsvSource({
			"벨벳 립스틱, LIP",
			"워터 틴트, LIP",
			"립 글로스, LIP",
			"치크 블러셔, CHEEK",
			"멀티 치크, CHEEK",
			"아이섀도 팔레트, EYE",
			"펜슬 아이라이너, EYE",
			"커버 쿠션, BASE",
			"롱웨어 파운데이션, BASE",
			"컨실러, BASE",
	})
	@DisplayName("색조 종류")
	void makeup(String name, MakeupType expected) {
		assertThat(ProductTypeGuesser.guessMakeup(name)).isEqualTo(expected);
	}
}
