package com.mycosmetic.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mycosmetic.entity.ShelfItem;

public interface ShelfItemRepository extends JpaRepository<ShelfItem, Long> {

	/** 남의 항목은 찾지 못하게 항상 사용자 ID와 함께 조회한다 */
	Optional<ShelfItem> findByIdAndUserId(Long id, Long userId);

	List<ShelfItem> findByUserIdOrderByUpdatedAtDesc(Long userId);

	/** 화장대 목록: 제품·성분 버전을 한 번에 읽는다. 최근에 바꾼 순 */
	@Query("select s from ShelfItem s join fetch s.product join fetch s.ingredientList where s.user.id = :userId order by s.updatedAt desc, s.id desc")
	List<ShelfItem> findWithProductByUserId(@Param("userId") Long userId);

	/** 루틴에 넣을 항목들: 내 것만 제품과 함께 읽는다 */
	@Query("select s from ShelfItem s join fetch s.product where s.user.id = :userId and s.id in :ids")
	List<ShelfItem> findWithProductByUserIdAndIdIn(@Param("userId") Long userId, @Param("ids") Collection<Long> ids);

	boolean existsByUserIdAndIngredientListId(Long userId, Long ingredientListId);

	Optional<ShelfItem> findByUserIdAndIngredientListId(Long userId, Long ingredientListId);

	/** 탈퇴 시 사용자의 행을 한 번에 지운다 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from ShelfItem s where s.user.id = :userId")
	int deleteAllByUserId(@Param("userId") Long userId);
}
