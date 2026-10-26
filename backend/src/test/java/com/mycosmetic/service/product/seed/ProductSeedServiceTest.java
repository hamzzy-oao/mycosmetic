package com.mycosmetic.service.product.seed;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.mycosmetic.entity.Ingredient;
import com.mycosmetic.entity.IngredientAlias;
import com.mycosmetic.entity.IngredientList;
import com.mycosmetic.entity.IngredientListItem;
import com.mycosmetic.entity.MatchType;
import com.mycosmetic.entity.Product;
import com.mycosmetic.repository.IngredientAliasRepository;
import com.mycosmetic.repository.IngredientListItemRepository;
import com.mycosmetic.repository.IngredientListRepository;
import com.mycosmetic.repository.ProductRepository;
import com.mycosmetic.service.ingredient.IngredientNameNormalizer;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.ProductFixtures;

/** 시드 넣기. 브랜드·성분 이름은 매번 고유하게 만들어 다른 테스트 데이터와 겹치지 않게 한다 */
class ProductSeedServiceTest extends IntegrationTestSupport {

	@Autowired
	ProductSeedService seedService;

	@Autowired
	ProductFixtures fixtures;

	@Autowired
	IngredientAliasRepository aliasRepository;

	@Autowired
	ProductRepository productRepository;

	@Autowired
	IngredientListRepository listRepository;

	@Autowired
	IngredientListItemRepository itemRepository;

	@Test
	@DisplayName("사전에 있는 성분은 잇고 없는 성분은 원문만 UNMATCHED로, 만든 사람 없이 확인 0회로 넣는다")
	void insertsProductAndListWithoutCreatorOrConfirmation() {
		String brand = "시드브랜드" + ProductFixtures.unique();
		Ingredient known = dictionary("시드성분" + ProductFixtures.unique());
		String unknown = "없는성분" + ProductFixtures.unique();

		SeedReport report = seedService.seed(List.of(row(2, brand, "수분  크림", known.getStandardName() + ", " + unknown)),
				false);

		assertThat(report.created()).isEqualTo(1);
		assertThat(report.createdProducts()).isEqualTo(1);
		assertThat(report.items()).containsEntry("EXACT", 1).containsEntry("UNMATCHED", 1);
		assertThat(report.unmatched()).extracting(SeedReport.NameCount::rawText).containsExactly(unknown);
		assertThat(report.byKind()).containsEntry("크림", 1);

		Product product = productRepository.findByBrandAndNameOrderByIdAsc(brand, "수분 크림").get(0);
		assertThat(product.getBarcode()).isNull();
		IngredientList list = listRepository.findByProductId(product.getId()).get(0);
		assertThat(list.getConfirmCount()).isZero();
		List<IngredientListItem> items = itemRepository.findWithIngredientByListId(list.getId());
		assertThat(items).extracting(IngredientListItem::getMatchType)
				.containsExactly(MatchType.EXACT, MatchType.UNMATCHED);
		assertThat(items.get(0).getIngredient().getId()).isEqualTo(known.getId());
		assertThat(items.get(1).getIngredient()).isNull();
	}

	@Test
	@DisplayName("다시 돌리면 같은 버전은 ALREADY_EXISTS, 성분이 바뀌면 같은 제품에 새 버전을 더한다")
	void rerunIsIdempotentAndAddsNewVersion() {
		String brand = "seedbrand" + ProductFixtures.unique();
		String a = "성분가" + ProductFixtures.unique();
		String b = "성분나" + ProductFixtures.unique();
		seedService.seed(List.of(row(2, brand, "토너", a + ", " + b)), false);

		SeedReport again = seedService.seed(List.of(row(2, brand.toUpperCase(), "토너", a + ", " + b)), false);
		SeedReport changed = seedService.seed(List.of(row(2, brand, "토너", b + ", " + a)), false);

		assertThat(again.alreadyExists()).isEqualTo(1);
		assertThat(again.created()).isZero();
		assertThat(changed.created()).isEqualTo(1);
		assertThat(changed.createdProducts()).isZero();
		List<Product> products = productRepository.findByBrandAndNameOrderByIdAsc(brand, "토너");
		assertThat(products).hasSize(1);
		assertThat(listRepository.findByProductId(products.get(0).getId())).hasSize(2);
	}

	@Test
	@DisplayName("dry-run은 결과만 세고 DB에 쓰지 않는다")
	void dryRunWritesNothing() {
		String brand = "시드브랜드" + ProductFixtures.unique();

		SeedReport report = seedService.seed(List.of(row(2, brand, "세럼", "정제수" + ProductFixtures.unique())), true);

		assertThat(report.dryRun()).isTrue();
		assertThat(report.created()).isEqualTo(1);
		assertThat(report.createdProducts()).isEqualTo(1);
		assertThat(productRepository.findByBrandAndNameOrderByIdAsc(brand, "세럼")).isEmpty();
	}

	@Test
	@DisplayName("형식 오류는 failed, 여러 구역·파일 안 중복·보관된 제품은 skipped로 남기고 나머지는 계속 넣는다")
	void reportsSkippedAndFailedLinesAndContinues() {
		String brand = "시드브랜드" + ProductFixtures.unique();
		Product archived = fixtures.product(null, brand, "보관 크림");
		archived.archive();
		productRepository.saveAndFlush(archived);
		String ok = "성분" + ProductFixtures.unique();

		SeedReport report = seedService.seed(List.of(
				new SeedRow(2, brand, "립", "", "BEAUTY", "", ok, "PACKAGE", ""),
				new SeedRow(3, brand, "쿠션", "", "MAKEUP", "", "[01호] " + ok + ", 탤크\n[02호] " + ok + ", 마이카",
						"PACKAGE", ""),
				row(4, brand, "보관 크림", ok),
				row(5, brand, "앰플", ok),
				row(6, brand, "앰플 ", ok),
				new SeedRow(7, brand, "미스트", "", "SKINCARE", "", ok, "CRAWLED", "")), false);

		assertThat(report.rows()).isEqualTo(6);
		assertThat(report.created()).isEqualTo(1);
		assertThat(report.failed()).extracting(SeedReport.LineReason::line, SeedReport.LineReason::reason)
				.containsExactly(org.assertj.core.groups.Tuple.tuple(2, "INVALID_CATEGORY"),
						org.assertj.core.groups.Tuple.tuple(7, "INVALID_SOURCE_TYPE"));
		assertThat(report.skipped()).extracting(SeedReport.LineReason::line, SeedReport.LineReason::reason)
				.containsExactly(org.assertj.core.groups.Tuple.tuple(3, "MULTI_SECTION"),
						org.assertj.core.groups.Tuple.tuple(4, "ARCHIVED"),
						org.assertj.core.groups.Tuple.tuple(6, "DUPLICATE_IN_FILE"));
		assertThat(productRepository.findByBrandAndNameOrderByIdAsc(brand, "보관 크림")).hasSize(1);
	}

	private static SeedRow row(int line, String brand, String name, String ingredients) {
		return new SeedRow(line, brand, name, "50ml", "SKINCARE", name.contains("크림") ? "크림" : "", ingredients,
				"PACKAGE", "");
	}

	private Ingredient dictionary(String standardName) {
		Ingredient ingredient = fixtures.ingredientNamed(standardName);
		aliasRepository.save(IngredientAlias.create(ingredient, standardName,
				IngredientNameNormalizer.normalize(standardName).value()));
		return ingredient;
	}
}
