package com.mycosmetic.service.compare;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.mycosmetic.dto.compare.CompareResponse;
import com.mycosmetic.entity.Ingredient;
import com.mycosmetic.entity.IngredientList;
import com.mycosmetic.entity.Product;
import com.mycosmetic.entity.User;
import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.service.shelf.ShelfService;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.ProductFixtures;
import com.mycosmetic.support.TestAuthHelper;

/** 제품 비교 (no-ai-features 설계 B) */
class CompareServiceTest extends IntegrationTestSupport {

	@Autowired
	CompareService compareService;

	@Autowired
	ShelfService shelfService;

	@Autowired
	TestAuthHelper auth;

	@Autowired
	ProductFixtures fixtures;

	private User newUser() {
		return auth.createUser(TestAuthHelper.uniqueEmail());
	}

	private static ErrorCode codeOf(Throwable e) {
		return ((BusinessException) e).errorCode();
	}

	@Test
	@DisplayName("화장대 항목 둘: 공통·A만·B만을 표준명(없으면 원문)으로, 성분 그룹과 함께")
	void compare_shelfItems() {
		User user = newUser();
		Ingredient water = fixtures.ingredient("정제수");
		Ingredient retinol = fixtures.ingredient("레티놀");
		Ingredient niacinamide = fixtures.ingredient("나이아신아마이드");
		Product pa = fixtures.product();
		Product pb = fixtures.product();
		IngredientList la = fixtures.list(pa, 1, water, retinol, "Glycolic  Acid");
		IngredientList lb = fixtures.list(pb, 1, niacinamide, "glycolic acid", water);
		Long a = shelfService.add(user.getId(), la.getId()).id();
		Long b = shelfService.add(user.getId(), lb.getId()).id();

		CompareResponse result = compareService.compare(user.getId(), a, null, b, null);

		assertThat(result.a().product().id()).isEqualTo(pa.getId());
		assertThat(result.a().listId()).isEqualTo(la.getId());
		assertThat(result.b().product().id()).isEqualTo(pb.getId());
		assertThat(result.b().listId()).isEqualTo(lb.getId());
		assertThat(result.common()).containsExactly(water.getStandardName(), "Glycolic  Acid");
		assertThat(result.onlyA()).containsExactly(retinol.getStandardName());
		assertThat(result.onlyB()).containsExactly(niacinamide.getStandardName());
		assertThat(result.groupsA()).containsExactly("RETINOID", "AHA");
		assertThat(result.groupsB()).containsExactly("AHA", "NIACINAMIDE");
	}

	@Test
	@DisplayName("제품 ID로 고르면 대표 버전으로 비교하고, 화장대 항목과 섞어 쓸 수 있다")
	void compare_products_useRepresentative() {
		User user = newUser();
		Product pa = fixtures.product();
		fixtures.list(pa, 1, "정제수");
		IngredientList newer = fixtures.list(pa, 2, "정제수", "글리세린");
		Product pb = fixtures.product();
		IngredientList lb = fixtures.list(pb, 1, "정제수");
		Long b = shelfService.add(user.getId(), lb.getId()).id();

		CompareResponse result = compareService.compare(user.getId(), null, pa.getId(), b, null);

		assertThat(result.a().listId()).isEqualTo(newer.getId());
		assertThat(result.a().product().representativeListId()).isEqualTo(newer.getId());
		assertThat(result.common()).containsExactly("정제수");
		assertThat(result.onlyA()).containsExactly("글리세린");
		assertThat(result.onlyB()).isEmpty();
		assertThat(result.groupsA()).isEmpty();
	}

	@Test
	@DisplayName("같은 항목끼리 비교하면 모두 공통")
	void compare_same() {
		User user = newUser();
		Long a = shelfService.add(user.getId(), fixtures.list(fixtures.product(), 1, "정제수", "글리세린").getId()).id();

		CompareResponse result = compareService.compare(user.getId(), a, null, a, null);

		assertThat(result.common()).containsExactly("정제수", "글리세린");
		assertThat(result.onlyA()).isEmpty();
		assertThat(result.onlyB()).isEmpty();
	}

	@Test
	@DisplayName("남의 화장대 항목, 없는 제품, 성분 버전이 없는 제품은 NOT_FOUND")
	void compare_notFound() {
		User a = newUser();
		User b = newUser();
		Long aItem = shelfService.add(a.getId(), fixtures.list(fixtures.product(), 1, "정제수").getId()).id();
		Long bItem = shelfService.add(b.getId(), fixtures.list(fixtures.product(), 1, "정제수").getId()).id();
		Product noList = fixtures.product();

		assertThatThrownBy(() -> compareService.compare(b.getId(), aItem, null, bItem, null))
				.extracting(CompareServiceTest::codeOf).isEqualTo(ErrorCode.NOT_FOUND);
		assertThatThrownBy(() -> compareService.compare(b.getId(), bItem, null, null, Long.MAX_VALUE))
				.extracting(CompareServiceTest::codeOf).isEqualTo(ErrorCode.NOT_FOUND);
		assertThatThrownBy(() -> compareService.compare(b.getId(), bItem, null, null, noList.getId()))
				.extracting(CompareServiceTest::codeOf).isEqualTo(ErrorCode.NOT_FOUND);
	}

	@Test
	@DisplayName("한쪽을 고르지 않았거나 항목·제품을 둘 다 주면 INVALID_REQUEST")
	void compare_invalidSides() {
		User user = newUser();
		Long a = shelfService.add(user.getId(), fixtures.list(fixtures.product(), 1, "정제수").getId()).id();

		assertThatThrownBy(() -> compareService.compare(user.getId(), a, null, null, null))
				.extracting(CompareServiceTest::codeOf).isEqualTo(ErrorCode.INVALID_REQUEST);
		assertThatThrownBy(() -> compareService.compare(user.getId(), a, 1L, a, null))
				.extracting(CompareServiceTest::codeOf).isEqualTo(ErrorCode.INVALID_REQUEST);
	}
}
