package com.mycosmetic.service.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.mycosmetic.dto.product.ProductDetailResponse;
import com.mycosmetic.dto.product.ProductDetailResponse.ItemView;
import com.mycosmetic.dto.product.ProductDetailResponse.ListBrief;
import com.mycosmetic.dto.product.ProductDetailResponse.ListView;
import com.mycosmetic.dto.product.ProductSummaryResponse;
import com.mycosmetic.entity.Ingredient;
import com.mycosmetic.entity.IngredientList;
import com.mycosmetic.entity.ListStatus;
import com.mycosmetic.entity.MatchType;
import com.mycosmetic.entity.Product;
import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.ProductFixtures;

/** 설계 4.1 1단계(바코드 조회), 4.6(검색·상세), 3.5-4(대표 버전) */
class ProductQueryServiceTest extends IntegrationTestSupport {

	@Autowired
	ProductQueryService queryService;

	@Autowired
	ProductFixtures fixtures;

	@Test
	@DisplayName("이름으로 찾으면 대표 버전 ID를 준다")
	void search_returnsRepresentativeListId() {
		Product product = fixtures.product();
		Ingredient water = fixtures.ingredient("정제수");
		fixtures.list(product, 1, water, "향료");
		IngredientList verified = fixtures.list(product, 2, water);

		ProductSummaryResponse found = queryService.search(product.getName()).get(0);

		assertThat(found.id()).isEqualTo(product.getId());
		assertThat(found.brand()).isEqualTo("브랜드");
		assertThat(found.name()).isEqualTo(product.getName());
		assertThat(found.volume()).isEqualTo("50ml");
		assertThat(found.representativeListId()).isEqualTo(verified.getId());
	}

	@Test
	@DisplayName("성분 버전이 없는 제품은 representativeListId가 null")
	void search_noLists_nullRepresentative() {
		Product product = fixtures.product();

		assertThat(queryService.search(product.getName()).get(0).representativeListId()).isNull();
	}

	@Test
	@DisplayName("이름 검색은 브랜드·제품명 부분 일치이고 대표 버전 ID를 준다")
	void search_partialMatch() {
		String token = ProductFixtures.unique();
		Product byBrand = fixtures.product(null, "브랜드" + token, "크림");
		Product byName = fixtures.product(null, "다른브랜드", "수분" + token + "크림");
		IngredientList list = fixtures.list(byName, 1, "정제수");

		List<ProductSummaryResponse> found = queryService.search(token.substring(2, 10));

		assertThat(found).extracting(ProductSummaryResponse::id)
				.containsExactlyInAnyOrder(byBrand.getId(), byName.getId());
		assertThat(found).filteredOn(p -> p.id().equals(byName.getId()))
				.extracting(ProductSummaryResponse::representativeListId).containsExactly(list.getId());
		assertThat(found).filteredOn(p -> p.id().equals(byBrand.getId()))
				.extracting(ProductSummaryResponse::representativeListId).containsNull();
	}

	@Test
	@DisplayName("이름 검색은 최대 20개")
	void search_max20() {
		String token = ProductFixtures.unique();
		for (int i = 0; i < 22; i++) {
			fixtures.product(null, "대량", token + "-" + i);
		}

		assertThat(queryService.search(token)).hasSize(20);
	}

	@Test
	@DisplayName("검색어의 %와 _는 글자 그대로 찾는다")
	void search_escapesWildcards() {
		String token = ProductFixtures.unique();
		fixtures.product(null, "브랜드", token + "abc");
		Product percent = fixtures.product(null, "브랜드", token + "%할인");

		assertThat(queryService.search(token + "_")).isEmpty();
		assertThat(queryService.search(token + "%")).extracting(ProductSummaryResponse::id)
				.containsExactly(percent.getId());
	}

	@Test
	@DisplayName("검색어가 2글자 미만이면 빈 목록")
	void search_shortQuery_empty() {
		assertThat(queryService.search("a")).isEmpty();
		assertThat(queryService.search("  ")).isEmpty();
		assertThat(queryService.search(null)).isEmpty();
	}

	@Test
	@DisplayName("상세는 대표 버전과 나머지 버전 목록")
	void detail_representativeAndOthers() {
		Product product = fixtures.product();
		Ingredient water = fixtures.ingredient("정제수");
		IngredientList pending = fixtures.list(product, 1, water, "향료");
		IngredientList verified = fixtures.list(product, 2, "향료", water);

		ProductDetailResponse detail = queryService.detail(product.getId());

		assertThat(detail.product().id()).isEqualTo(product.getId());
		assertThat(detail.product().representativeListId()).isEqualTo(verified.getId());
		assertThat(detail.representative().id()).isEqualTo(verified.getId());
		assertThat(detail.representative().status()).isEqualTo(ListStatus.VERIFIED);
		assertThat(detail.representative().confirmCount()).isEqualTo(2);
		assertThat(detail.representative().items()).containsExactly(
				new ItemView(1, null, "향료", MatchType.UNMATCHED),
				new ItemView(2, water.getId(), water.getStandardName(), MatchType.EXACT));
		assertThat(detail.otherLists()).extracting(ListBrief::id).containsExactly(pending.getId());
		assertThat(detail.otherLists().get(0).status()).isEqualTo(ListStatus.PENDING);
		assertThat(detail.otherLists().get(0).createdAt()).isNotNull();
	}

	@Test
	@DisplayName("버전이 없는 제품의 상세는 representative null, otherLists 빈 목록")
	void detail_noLists() {
		Product product = fixtures.product();

		ProductDetailResponse detail = queryService.detail(product.getId());

		assertThat(detail.representative()).isNull();
		assertThat(detail.otherLists()).isEmpty();
	}

	@Test
	@DisplayName("없는 제품의 상세는 404")
	void detail_missing_notFound() {
		assertThatThrownBy(() -> queryService.detail(Long.MAX_VALUE))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).errorCode())
				.isEqualTo(ErrorCode.NOT_FOUND);
	}

	@Test
	@DisplayName("특정 버전 조회는 대표가 아닌 버전도 성분 줄과 함께 준다")
	void list_nonRepresentativeVersion() {
		Product product = fixtures.product();
		Ingredient water = fixtures.ingredient("정제수");
		IngredientList pending = fixtures.list(product, 1, water, "향료");
		fixtures.list(product, 2, "향료", water);

		ListView view = queryService.list(product.getId(), pending.getId());

		assertThat(view.id()).isEqualTo(pending.getId());
		assertThat(view.status()).isEqualTo(ListStatus.PENDING);
		assertThat(view.confirmCount()).isEqualTo(1);
		assertThat(view.items()).containsExactly(
				new ItemView(1, water.getId(), water.getStandardName(), MatchType.EXACT),
				new ItemView(2, null, "향료", MatchType.UNMATCHED));
	}

	@Test
	@DisplayName("다른 제품의 버전이나 없는 버전은 404")
	void list_otherProductOrMissing_notFound() {
		Product product = fixtures.product();
		Product other = fixtures.product();
		IngredientList otherList = fixtures.list(other, 1, "정제수");

		assertThatThrownBy(() -> queryService.list(product.getId(), otherList.getId()))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).errorCode())
				.isEqualTo(ErrorCode.NOT_FOUND);
		assertThatThrownBy(() -> queryService.list(product.getId(), Long.MAX_VALUE))
				.isInstanceOf(BusinessException.class)
				.extracting(e -> ((BusinessException) e).errorCode())
				.isEqualTo(ErrorCode.NOT_FOUND);
	}
}
