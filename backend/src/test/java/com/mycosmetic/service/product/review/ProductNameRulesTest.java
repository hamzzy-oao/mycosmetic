package com.mycosmetic.service.product.review;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mycosmetic.service.product.review.ProductNameRules.Cleaned;
import com.mycosmetic.service.product.review.ProductNameRules.Reason;

/** 올리브영식 제품명 표기 규칙. 예시는 개발 DB에 실제로 들어온 이름이다 */
class ProductNameRulesTest {

	@Test
	@DisplayName("이름 앞의 브랜드와 용량을 빼고, 용량은 따로 돌려준다. 리필은 남긴다")
	void brandAndVolume() {
		Cleaned c = ProductNameRules.clean("일리윤", "일리윤 세라마이드 아토 6.0 탑투토 워시 500ml 리필");

		assertThat(c.name()).isEqualTo("세라마이드 아토 6.0 탑투토 워시 리필");
		assertThat(c.volume()).isEqualTo("500ml");
		assertThat(c.reasons()).containsExactly(Reason.BRAND_IN_NAME, Reason.VOLUME_IN_NAME);
	}

	@Test
	@DisplayName("구성품 꼬리, 대괄호·괄호 속 판매 문구, 기획·증정 같은 말을 뺀다")
	void promoText() {
		assertThat(ProductNameRules.clean("일리윤", "세라마이드 탑투토워시 1개 + 리필 2개 구성").name())
				.isEqualTo("세라마이드 탑투토워시");

		Cleaned c = ProductNameRules.clean("설화수", "[단독기획] 윤조에센스 60ml 기획 (+증정)");
		assertThat(c.name()).isEqualTo("윤조에센스");
		assertThat(c.volume()).isEqualTo("60ml");
		assertThat(c.reasons()).containsExactly(Reason.VOLUME_IN_NAME, Reason.PROMO_TEXT);
	}

	@Test
	@DisplayName("브랜드는 공백·대소문자를 무시하고 찾는다")
	void brandIgnoresCaseAndSpaces() {
		assertThat(ProductNameRules.clean("primera", "PRIMERA 워터리 에센스").name()).isEqualTo("워터리 에센스");
		assertThat(ProductNameRules.clean("닥터 지", "닥터지 레드 블레미쉬 크림").name()).isEqualTo("레드 블레미쉬 크림");
	}

	@Test
	@DisplayName("규칙에 맞는 이름, 다 빼면 아무것도 안 남는 이름은 그대로 둔다")
	void keepsGoodNames() {
		Cleaned ok = ProductNameRules.clean("설화수", "윤조에센스");
		assertThat(ok.name()).isEqualTo("윤조에센스");
		assertThat(ok.reasons()).isEmpty();

		Cleaned onlyBrand = ProductNameRules.clean("설화수", "설화수");
		assertThat(onlyBrand.name()).isEqualTo("설화수");
		assertThat(onlyBrand.reasons()).isEmpty();
	}

	@Test
	@DisplayName("단위가 없는 숫자(판테놀 5, 레티놀 0.1)는 용량으로 보지 않는다")
	void numbersWithoutUnit() {
		assertThat(ProductNameRules.clean("브랜드", "판테놀 5 크림").reasons()).isEmpty();
		assertThat(ProductNameRules.clean("브랜드", "레티놀 0.1 세럼").reasons()).isEmpty();
	}

	@Test
	@DisplayName("중복 의심 비교: 표기 규칙을 적용하고 리필·공백을 빼면 같다")
	void duplicateKey() {
		assertThat(ProductNameRules.duplicateKey("설화수", "진설크림 리필"))
				.isEqualTo(ProductNameRules.duplicateKey("설화수", "설화수 진설 크림 60ml"));
		assertThat(ProductNameRules.duplicateKey("설화수", "진설크림 리치"))
				.isNotEqualTo(ProductNameRules.duplicateKey("설화수", "진설크림"));
	}
}
