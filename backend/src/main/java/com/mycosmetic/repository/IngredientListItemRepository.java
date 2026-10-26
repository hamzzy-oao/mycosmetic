package com.mycosmetic.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mycosmetic.entity.IngredientListItem;

public interface IngredientListItemRepository extends JpaRepository<IngredientListItem, Long> {

	List<IngredientListItem> findByListIdOrderByPosition(Long listId);

	/** 성분 줄과 매칭된 성분(표준명)을 한 번에 읽는다 */
	@Query("select i from IngredientListItem i left join fetch i.ingredient where i.list.id = :listId order by i.position")
	List<IngredientListItem> findWithIngredientByListId(@Param("listId") Long listId);

	/** 사전에 없는 줄이 하나라도 있는 버전 id(다시 매칭) */
	@Query("select distinct i.list.id from IngredientListItem i where i.matchType = com.mycosmetic.entity.MatchType.UNMATCHED")
	List<Long> findListIdsWithUnmatched();

	/** 여러 버전의 성분 줄을 표준명과 함께 한 번에 읽는다(루틴 충돌 판정) */
	@Query("select i from IngredientListItem i left join fetch i.ingredient where i.list.id in :listIds")
	List<IngredientListItem> findWithIngredientByListIdIn(@Param("listIds") Collection<Long> listIds);
}
