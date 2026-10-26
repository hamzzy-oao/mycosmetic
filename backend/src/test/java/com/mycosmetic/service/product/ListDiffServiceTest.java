package com.mycosmetic.service.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.mycosmetic.dto.product.ListDiffResponse;
import com.mycosmetic.entity.Ingredient;
import com.mycosmetic.entity.IngredientList;
import com.mycosmetic.entity.Product;
import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.ProductFixtures;

/** 두 성분 버전의 차이. 비교 키는 fingerprint 토큰(ListFingerprint.token)과 같다 */
class ListDiffServiceTest extends IntegrationTestSupport {

	@Autowired
	ListDiffService diffService;

	@Autowired
	ProductFixtures fixtures;

	@Test
	@DisplayName("diff: 추가·삭제된 성분과 순서 변경 여부")
	void diff_addedRemovedOrder() {
		Product product = fixtures.product();
		Ingredient water = fixtures.ingredient("정제수");
		Ingredient glycerin = fixtures.ingredient("글리세린");
		Ingredient niacin = fixtures.ingredient("나이아신아마이드");
		IngredientList from = fixtures.list(product, 1, water, glycerin, "Foo  Bar");
		IngredientList to = fixtures.list(product, 1, glycerin, water, "foo bar", niacin, "향료");

		ListDiffResponse diff = diffService.diff(product.getId(), from.getId(), to.getId());

		// "Foo  Bar"와 "foo bar"는 정규화하면 같은 토큰이라 바뀌지 않은 것으로 본다
		assertThat(diff.added()).containsExactly(niacin.getStandardName(), "향료");
		assertThat(diff.removed()).isEmpty();
		assertThat(diff.orderChanged()).isTrue();

		ListDiffResponse back = diffService.diff(product.getId(), to.getId(), from.getId());
		assertThat(back.added()).isEmpty();
		assertThat(back.removed()).containsExactly(niacin.getStandardName(), "향료");
		assertThat(back.orderChanged()).isTrue();
	}

	@Test
	@DisplayName("diff: 빠진 성분만 있고 남은 성분 순서가 같으면 orderChanged false")
	void diff_sameOrder() {
		Product product = fixtures.product();
		Ingredient water = fixtures.ingredient("정제수");
		Ingredient glycerin = fixtures.ingredient("글리세린");
		IngredientList from = fixtures.list(product, 1, water, "향료", glycerin);
		IngredientList to = fixtures.list(product, 1, water, glycerin);

		ListDiffResponse diff = diffService.diff(product.getId(), from.getId(), to.getId());

		assertThat(diff.added()).isEmpty();
		assertThat(diff.removed()).containsExactly("향료");
		assertThat(diff.orderChanged()).isFalse();
	}

	@Test
	@DisplayName("diff: 다른 제품의 버전 ID면 404")
	void diff_otherProductsList_notFound() {
		Product product = fixtures.product();
		Product other = fixtures.product();
		IngredientList mine = fixtures.list(product, 1, "정제수");
		IngredientList theirs = fixtures.list(other, 1, "글리세린");

		assertThatThrownBy(() -> diffService.diff(product.getId(), mine.getId(), theirs.getId()))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).errorCode())
				.isEqualTo(ErrorCode.NOT_FOUND);
		assertThatThrownBy(() -> diffService.diff(product.getId(), Long.MAX_VALUE, mine.getId()))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).errorCode())
				.isEqualTo(ErrorCode.NOT_FOUND);
	}
}
