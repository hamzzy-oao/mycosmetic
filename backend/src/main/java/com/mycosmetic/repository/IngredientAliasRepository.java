package com.mycosmetic.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mycosmetic.entity.IngredientAlias;

public interface IngredientAliasRepository extends JpaRepository<IngredientAlias, Long>, IngredientAliasSearch {

	/** EXACT·ALIAS 매칭. normalized_alias는 utf8mb4_bin이라 정확히 같은 문자열만 찾는다 */
	List<IngredientAlias> findByNormalizedAlias(String normalizedAlias);

	/** 확인·저장에서 성분별 매칭 종류(ALIAS인지)를 한 번에 판정한다 */
	@Query("select a from IngredientAlias a where a.ingredient.id in :ingredientIds and a.normalizedAlias in :names")
	List<IngredientAlias> findByIngredientIdInAndNormalizedAliasIn(@Param("ingredientIds") Collection<Long> ingredientIds,
			@Param("names") Collection<String> names);
}
