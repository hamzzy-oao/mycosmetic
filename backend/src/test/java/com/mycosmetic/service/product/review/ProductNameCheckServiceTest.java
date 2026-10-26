package com.mycosmetic.service.product.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.mycosmetic.dto.product.NameCheckResponse;
import com.mycosmetic.dto.product.NameCheckResponse.SimilarProduct;
import com.mycosmetic.entity.Category;
import com.mycosmetic.entity.Product;
import com.mycosmetic.entity.ProductSource;
import com.mycosmetic.entity.User;
import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.repository.ProductRepository;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.ProductFixtures;
import com.mycosmetic.support.TestAuthHelper;

/** 새 제품 이름 점검(2026-10-26). 테스트끼리 DB를 같이 쓰므로 브랜드 이름을 매번 고유하게 만든다 */
class ProductNameCheckServiceTest extends IntegrationTestSupport {

	@Autowired
	ProductNameCheckService service;

	@Autowired
	ProductRepository productRepository;

	@Autowired
	ProductFixtures fixtures;

	@Autowired
	TestAuthHelper auth;

	/** 한글만으로 된 고유 브랜드(편집 거리 비교가 다른 테스트 브랜드와 겹치지 않게 길게) */
	private static String brand() {
		return "점검브랜드" + ProductFixtures.unique();
	}

	private Product seed(String brand, String name) {
		return fixtures.product(ProductFixtures.newBarcode(), brand, name);
	}

	@Test
	@DisplayName("이름을 표기 규칙으로 정리해 보여 주고, 입력한 용량이 없으면 이름 속 용량을 제안한다")
	void cleansName() {
		String b = brand();
		NameCheckResponse r = service.check(b, b + " 윤조에센스 60ml [단독기획]", null);

		assertThat(r.name()).isEqualTo("윤조에센스");
		assertThat(r.volume()).isEqualTo("60ml");
		assertThat(r.reasons()).hasSize(3);
		assertThat(service.check(b, "윤조에센스 60ml", "60ml").volume()).as("입력한 용량이 있으면 제안하지 않는다").isNull();
		assertThat(service.check(b, "윤조에센스", null).reasons()).isEmpty();
	}

	@Test
	@DisplayName("같은 브랜드에 같은 이름(리필·공백·용량 무시)이나 오타 같은 이름이 있으면 먼저 보여 준다")
	void similarProducts() {
		String b = brand();
		Product same = seed(b, "윤조 에센스");
		Product refill = seed(b, "윤조에센스 리필");
		Product typo = seed(b, "윤조에센쓰");
		Product other = seed(b, "자음생크림");

		NameCheckResponse r = service.check(b, b + " 윤조에센스 60ml", null);

		assertThat(r.brandKnown()).isTrue();
		assertThat(r.brandSuggestion()).isNull();
		assertThat(r.similar()).extracting(s -> s.product().id())
				.containsExactly(same.getId(), refill.getId(), typo.getId())
				.doesNotContain(other.getId());
		assertThat(r.similar()).extracting(SimilarProduct::sameName).containsExactly(true, true, false);
		assertThat(r.similar().get(2).similarity()).isBetween(70, 99);
		assertThat(r.needsAttention()).isTrue();
	}

	@Test
	@DisplayName("브랜드 표기가 다르면(공백) 등록된 표기를, 처음 보는 브랜드가 비슷하면 그 브랜드를 제안한다")
	void brandSuggestion() {
		String b = brand();
		seed(b, "수분크림");
		seed(b, "진정토너");
		String spaced = b.substring(0, 3) + " " + b.substring(3);

		NameCheckResponse spacing = service.check(spaced, "수분크림", null);
		assertThat(spacing.brandKnown()).isTrue();
		assertThat(spacing.brandSuggestion()).isEqualTo(b);
		assertThat(spacing.similar()).as("제안한 브랜드 안에서 비슷한 제품을 찾는다").hasSize(1);

		String typo = b.substring(0, b.length() - 1) + "가";
		NameCheckResponse unknown = service.check(typo, "새제품", null);
		assertThat(unknown.brandKnown()).isFalse();
		assertThat(unknown.brandSuggestion()).isEqualTo(b);

		NameCheckResponse brandNew = service.check("완전히새로운" + ProductFixtures.unique() + "브랜드", "크림", null);
		assertThat(brandNew.brandKnown()).isFalse();
		assertThat(brandNew.brandSuggestion()).isNull();
		assertThat(brandNew.similar()).isEmpty();
	}

	@Test
	@DisplayName("보관한 제품은 비교하지 않고, 브랜드 목록에도 넣지 않는다")
	void archivedIgnored() {
		String b = brand();
		Product archived = seed(b, "수분크림");
		archived.archive();
		productRepository.saveAndFlush(archived);

		assertThat(service.check(b, "수분크림", null).similar()).isEmpty();
		assertThat(service.brands()).doesNotContain(b);
		assertThat(service.sameNameProduct(b, "수분크림")).isEmpty();
	}

	@Test
	@DisplayName("브랜드 목록은 제품이 많은 순이다")
	void brands() {
		String many = brand();
		String few = brand();
		seed(many, "가");
		seed(many, "나");
		seed(few, "다");

		assertThat(service.brands()).containsSubsequence(many, few);
	}

	@Test
	@DisplayName("같은 이름 제품 찾기: 표기 규칙·리필·공백을 무시한다")
	void sameNameProduct() {
		String b = brand();
		Product p = seed(b, "진설크림");

		assertThat(service.sameNameProduct(b, b + " 진설 크림 60ml").map(Product::getId)).contains(p.getId());
		assertThat(service.sameNameProduct(b, "진설크림 리치")).isEmpty();
	}

	@Test
	@DisplayName("시드 제품은 검수됨(SEED), 사용자 제품은 검수 전(USER). 링크 이름 그대로면 본인 제품만 LINK로 바꾼다")
	void sourceAndLink() {
		User owner = auth.createUser(TestAuthHelper.uniqueEmail());
		User other = auth.createUser(TestAuthHelper.uniqueEmail());
		Product seeded = seed(brand(), "크림");
		Product mine = productRepository.save(Product.create(null, brand(), "앰플", null, Category.SKINCARE, owner));

		assertThat(seeded.getSource()).isEqualTo(ProductSource.SEED);
		assertThat(seeded.isVerified()).isTrue();
		assertThat(mine.getSource()).isEqualTo(ProductSource.USER);
		assertThat(mine.isVerified()).isFalse();

		service.markFromLink(mine.getId(), other.getId());
		assertThat(productRepository.findById(mine.getId()).orElseThrow().getSource()).isEqualTo(ProductSource.USER);

		service.markFromLink(mine.getId(), owner.getId());
		Product reloaded = productRepository.findById(mine.getId()).orElseThrow();
		assertThat(reloaded.getSource()).isEqualTo(ProductSource.LINK);
		assertThat(reloaded.isVerified()).isTrue();
	}

	@Test
	@DisplayName("링크 이름과 같은지: 쇼핑몰 표기(브랜드·용량·기획 문구)를 빼고 비교한다")
	void sameAsLinkName() {
		assertThat(ProductNameCheckService.sameAsLinkName("설화수", "윤조에센스", "[단독기획] 설화수 윤조에센스 60ml")).isTrue();
		assertThat(ProductNameCheckService.sameAsLinkName("설화수", "윤조에센쓰", "설화수 윤조에센스 60ml")).isFalse();
		assertThat(ProductNameCheckService.sameAsLinkName("설화수", "윤조에센스", null)).isFalse();
	}

	@Test
	@DisplayName("브랜드·제품명이 비었으면 INVALID_REQUEST")
	void invalid() {
		assertThatThrownBy(() -> service.check(" ", "크림", null))
				.extracting(e -> ((BusinessException) e).errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
		assertThatThrownBy(() -> service.check("브랜드", "", null))
				.extracting(e -> ((BusinessException) e).errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST);
	}
}
