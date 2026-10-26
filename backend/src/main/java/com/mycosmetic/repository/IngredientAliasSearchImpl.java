package com.mycosmetic.repository;

import java.util.List;

import com.mycosmetic.entity.IngredientAlias;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;

class IngredientAliasSearchImpl implements IngredientAliasSearch {

	private static final String SEARCH_SQL = """
			SELECT * FROM ingredient_aliases
			WHERE MATCH(normalized_alias) AGAINST (:q IN NATURAL LANGUAGE MODE)
			ORDER BY MATCH(normalized_alias) AGAINST (:q IN NATURAL LANGUAGE MODE) DESC
			LIMIT :limit
			""";

	@PersistenceContext
	private EntityManager entityManager;

	@Override
	@SuppressWarnings("unchecked")
	public List<IngredientAlias> searchCandidates(String q, int limit) {
		return entityManager.createNativeQuery(SEARCH_SQL, IngredientAlias.class)
				.setParameter("q", q)
				.setParameter("limit", limit)
				.getResultList();
	}
}
