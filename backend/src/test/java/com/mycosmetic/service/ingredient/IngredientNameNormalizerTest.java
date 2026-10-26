package com.mycosmetic.service.ingredient;

import static org.assertj.core.api.Assertions.assertThat;

import java.text.Normalizer;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

/** 설계 4.2. extraction-eval 스킬 metrics.py의 normalize()와 같은 결과여야 한다 */
class IngredientNameNormalizerTest {

	@ParameterizedTest(name = "[{index}] \"{0}\" → \"{1}\"")
	@CsvSource({
			"'  정제수 ', 정제수",
			"티타늄디옥사이드(CI 77891), 티타늄디옥사이드",
			"'1,2-헥산다이올', '1,2-헥산다이올'",
			"'1,2–헥산다이올', '1,2-헥산다이올'",
			"피이지ㆍ100스테아레이트, 피이지·100스테아레이트",
			"Sodium  Hyaluronate, sodium hyaluronate",
			"하이알루로닉애씨드（저분자）, 하이알루로닉애씨드"
	})
	@DisplayName("공백·하이픈·가운뎃점·괄호·대소문자를 정리한다")
	void normalizes(String raw, String expected) {
		assertThat(IngredientNameNormalizer.normalize(raw).value()).isEqualTo(expected);
	}

	@Test
	@DisplayName("괄호 안 표기는 parentheticals로 보관한다")
	void keepsParentheticals() {
		NormalizedName name = IngredientNameNormalizer.normalize("티타늄디옥사이드(CI 77891)");

		assertThat(name.parentheticals()).containsExactly("CI 77891");
	}

	@Test
	@DisplayName("전각 괄호 안 표기도 parentheticals로 보관한다")
	void keepsFullWidthParentheticals() {
		NormalizedName name = IngredientNameNormalizer.normalize("하이알루로닉애씨드（저분자）");

		assertThat(name.parentheticals()).containsExactly("저분자");
	}

	@Test
	@DisplayName("NFD로 들어온 한글도 NFC 결과와 같다")
	void nfdEqualsNfc() {
		String nfd = Normalizer.normalize("나이아신아마이드", Normalizer.Form.NFD);

		assertThat(nfd).isNotEqualTo("나이아신아마이드");
		assertThat(IngredientNameNormalizer.normalize(nfd).value()).isEqualTo("나이아신아마이드");
	}

	@Test
	@DisplayName("괄호를 뗀 자리는 공백이 되어 앞뒤 글자가 붙지 않는다")
	void parenthesisBecomesSpace() {
		assertThat(IngredientNameNormalizer.normalize("A(x)B").value()).isEqualTo("a b");
	}

	@Test
	@DisplayName("전각 공백 같은 유니코드 공백도 하나의 공백으로 바꾼다")
	void unicodeWhitespace() {
		assertThat(IngredientNameNormalizer.normalize("정제수　 글리세린\t").value()).isEqualTo("정제수 글리세린");
	}
}
