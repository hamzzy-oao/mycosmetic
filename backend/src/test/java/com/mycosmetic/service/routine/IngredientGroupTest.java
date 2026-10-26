package com.mycosmetic.service.routine;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class IngredientGroupTest {

	@ParameterizedTest(name = "{0} → {1}")
	@CsvSource({
			"레티놀, RETINOID", "레티날, RETINOID", "레티닐팔미테이트, RETINOID",
			"트레티노인, RETINOID", "아다팔렌, RETINOID", "Retinol, RETINOID",
			"글라이콜릭애씨드, AHA", "락틱애씨드, AHA", "만델릭애씨드, AHA", "Glycolic Acid, AHA",
			"살리실릭애씨드, BHA", "베타인살리실레이트, BHA", "Salicylic Acid, BHA",
			"아스코빅애씨드, VITAMIN_C", "아스코빌글루코사이드, VITAMIN_C", "에칠아스코빌에텔, VITAMIN_C",
			"벤조일퍼옥사이드, BENZOYL_PEROXIDE", "나이아신아마이드, NIACINAMIDE", "NIACINAMIDE, NIACINAMIDE" })
	@DisplayName("정규화 이름에 키워드가 들어 있으면 그 그룹이다(한국어·영문, 대소문자 무관)")
	void detectsEachGroup(String name, IngredientGroup expected) {
		assertThat(IngredientGroup.of(name)).containsExactly(expected);
	}

	@Test
	@DisplayName("띄어 쓴 이름도 공백을 빼고 비교한다")
	void ignoresSpaces() {
		assertThat(IngredientGroup.of("글라이콜릭 애씨드")).containsExactly(IngredientGroup.AHA);
		assertThat(IngredientGroup.of("살리실릭  애씨드(BHA)")).containsExactly(IngredientGroup.BHA);
	}

	@Test
	@DisplayName("관계없는 성분, 빈 이름, null은 그룹이 없다")
	void noGroup() {
		assertThat(IngredientGroup.of("정제수")).isEmpty();
		assertThat(IngredientGroup.of("  ")).isEmpty();
		assertThat(IngredientGroup.of(null)).isEmpty();
	}

	@Test
	@DisplayName("전성분 목록은 각 성분 그룹의 합집합이다")
	void detectUnion() {
		assertThat(IngredientGroup.detect(List.of("정제수", "레티놀", "나이아신아마이드", "레티닐팔미테이트")))
				.containsExactlyInAnyOrder(IngredientGroup.RETINOID, IngredientGroup.NIACINAMIDE);
		assertThat(IngredientGroup.detect(List.of())).isEmpty();
	}
}
