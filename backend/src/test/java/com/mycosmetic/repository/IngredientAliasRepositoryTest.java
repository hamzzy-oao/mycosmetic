package com.mycosmetic.repository;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.mycosmetic.entity.Ingredient;
import com.mycosmetic.entity.IngredientAlias;
import com.mycosmetic.support.IntegrationTestSupport;

/**
 * InnoDB FULLTEXT는 커밋된 행만 찾는다. 그래서 @Transactional 없이 저장(커밋)한 뒤 검색하고, 넣은 행은 끝나고 지운다.
 */
class IngredientAliasRepositoryTest extends IntegrationTestSupport {

	@Autowired
	IngredientRepository ingredientRepository;

	@Autowired
	IngredientAliasRepository aliasRepository;

	private final List<IngredientAlias> savedAliases = new ArrayList<>();
	private final List<Ingredient> savedIngredients = new ArrayList<>();

	@AfterEach
	void cleanUp() {
		aliasRepository.deleteAllInBatch(savedAliases);
		ingredientRepository.deleteAllInBatch(savedIngredients);
	}

	@Test
	@DisplayName("한 글자 틀린 이름으로도 FULLTEXT 후보에 정답이 들어온다")
	void typoStillFindsCandidate() {
		IngredientAlias answer = saveAlias("나이아신아마이드");

		List<IngredientAlias> candidates = aliasRepository.searchCandidates("나이아신아마이트", 20);

		assertThat(candidates).extracting(IngredientAlias::getId).contains(answer.getId());
	}

	@Test
	@DisplayName("후보는 최대 20개")
	void candidatesAreLimitedTo20() {
		for (int i = 0; i < 25; i++) {
			saveAlias("히알루론산나트륨" + (char) ('가' + i * 28));
		}

		List<IngredientAlias> candidates = aliasRepository.searchCandidates("히알루론산", 20);

		assertThat(candidates).hasSize(20);
	}

	private IngredientAlias saveAlias(String normalizedAlias) {
		Ingredient ingredient = ingredientRepository.save(
				Ingredient.create(normalizedAlias + "-" + UUID.randomUUID(), null, null));
		savedIngredients.add(ingredient);
		IngredientAlias alias = aliasRepository.save(IngredientAlias.create(ingredient, normalizedAlias, normalizedAlias));
		savedAliases.add(alias);
		return alias;
	}
}
