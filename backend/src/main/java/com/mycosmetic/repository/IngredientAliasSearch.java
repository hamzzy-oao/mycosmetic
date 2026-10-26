package com.mycosmetic.repository;

import java.util.List;

import com.mycosmetic.entity.IngredientAlias;

/**
 * FULLTEXT 검색 조각. MATCH ... AGAINST는 이클립스 Spring Tools의 SQL 검사기가 읽지 못해서
 * {@code @Query} 대신 {@link IngredientAliasSearchImpl}에서 직접 실행한다.
 */
public interface IngredientAliasSearch {

	/** FUZZY 후보. ngram FULLTEXT로 비슷한 이름을 관련도 순으로 limit개까지 가져온다 */
	List<IngredientAlias> searchCandidates(String q, int limit);
}
