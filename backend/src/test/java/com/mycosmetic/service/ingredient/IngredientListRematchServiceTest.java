package com.mycosmetic.service.ingredient;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.mycosmetic.dto.product.FinalItem;
import com.mycosmetic.entity.Ingredient;
import com.mycosmetic.entity.IngredientAlias;
import com.mycosmetic.entity.IngredientList;
import com.mycosmetic.entity.IngredientListItem;
import com.mycosmetic.entity.MatchType;
import com.mycosmetic.entity.Product;
import com.mycosmetic.repository.IngredientAliasRepository;
import com.mycosmetic.repository.IngredientListItemRepository;
import com.mycosmetic.repository.IngredientListRepository;
import com.mycosmetic.service.product.ListFingerprint;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.ProductFixtures;

/**
 * 사전이 늘어난 뒤 저장된 버전의 "사전에 없음" 줄을 다시 매칭한다. 이름은 매번 고유하게 만들어 다른 테스트 데이터와 겹치지 않게 한다.
 */
class IngredientListRematchServiceTest extends IntegrationTestSupport {

	@Autowired
	IngredientListRematchService rematchService;

	@Autowired
	ProductFixtures fixtures;

	@Autowired
	IngredientAliasRepository aliasRepository;

	@Autowired
	IngredientListRepository listRepository;

	@Autowired
	IngredientListItemRepository itemRepository;

	@Test
	@DisplayName("사전에 생긴 표준명·이명 줄을 성분에 잇고, 원문은 그대로 두고 fingerprint를 다시 계산한다")
	void linksStandardNameAndAliasAndRecomputesFingerprint() {
		String unknown = "모르는성분" + ProductFixtures.unique();
		String synonym = "옛이름" + ProductFixtures.unique();
		Product product = fixtures.product();
		IngredientList list = fixtures.list(product, 1, "글리세린 새" + ProductFixtures.unique(), synonym, unknown);
		String oldFingerprint = list.getFingerprint();

		List<IngredientListItem> before = itemRepository.findByListIdOrderByPosition(list.getId());
		Ingredient standard = dictionary(before.get(0).getRawText());
		Ingredient renamed = dictionary("새이름" + ProductFixtures.unique());
		alias(renamed, synonym);

		rematchService.rematchUnmatched();

		List<IngredientListItem> after = itemRepository.findWithIngredientByListId(list.getId());
		assertThat(after).extracting(IngredientListItem::getRawText)
				.containsExactly(before.get(0).getRawText(), synonym, unknown);
		assertThat(after).extracting(IngredientListItem::getMatchType)
				.containsExactly(MatchType.EXACT, MatchType.ALIAS, MatchType.UNMATCHED);
		assertThat(after.get(0).getIngredient().getId()).isEqualTo(standard.getId());
		assertThat(after.get(1).getIngredient().getId()).isEqualTo(renamed.getId());

		IngredientList reloaded = listRepository.findById(list.getId()).orElseThrow();
		String expected = ListFingerprint.of(List.of(new FinalItem(standard.getId(), before.get(0).getRawText()),
				new FinalItem(renamed.getId(), synonym), new FinalItem(null, unknown)));
		assertThat(reloaded.getFingerprint()).isEqualTo(expected).isNotEqualTo(oldFingerprint);
		assertThat(reloaded.getConfirmCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("다시 계산한 fingerprint가 같은 제품의 다른 버전과 같으면 그 버전은 바꾸지 않는다")
	void skipsWhenAnotherVersionHasSameFingerprint() {
		String name = "겹치는성분" + ProductFixtures.unique();
		Product product = fixtures.product();
		IngredientList unmatched = fixtures.list(product, 0, name);
		Ingredient ingredient = dictionary(name);
		fixtures.list(product, 0, ingredient);
		String oldFingerprint = unmatched.getFingerprint();

		rematchService.rematchUnmatched();

		assertThat(listRepository.findById(unmatched.getId()).orElseThrow().getFingerprint())
				.isEqualTo(oldFingerprint);
		assertThat(itemRepository.findByListIdOrderByPosition(unmatched.getId()))
				.extracting(IngredientListItem::getMatchType)
				.containsExactly(MatchType.UNMATCHED);
	}

	@Test
	@DisplayName("사전에 여전히 없는 줄만 있으면 아무것도 바꾸지 않는다")
	void leavesListWithoutMatchesUntouched() {
		IngredientList list = fixtures.list(fixtures.product(), 0, "없는성분" + ProductFixtures.unique());
		String oldFingerprint = list.getFingerprint();

		rematchService.rematchUnmatched();

		assertThat(listRepository.findById(list.getId()).orElseThrow().getFingerprint()).isEqualTo(oldFingerprint);
	}

	/** 가져오기처럼 표준명도 alias 한 줄로 넣는다 */
	private Ingredient dictionary(String standardName) {
		Ingredient ingredient = fixtures.ingredientNamed(standardName);
		alias(ingredient, standardName);
		return ingredient;
	}

	private void alias(Ingredient ingredient, String alias) {
		aliasRepository.save(IngredientAlias.create(ingredient, alias,
				IngredientNameNormalizer.normalize(alias).value()));
	}
}
