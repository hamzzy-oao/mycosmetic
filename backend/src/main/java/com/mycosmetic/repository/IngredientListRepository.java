package com.mycosmetic.repository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mycosmetic.entity.IngredientList;

import jakarta.persistence.LockModeType;

public interface IngredientListRepository extends JpaRepository<IngredientList, Long> {

	Optional<IngredientList> findByProductIdAndFingerprint(Long productId, String fingerprint);

	/**
	 * 확인·저장에서 id만 먼저 찾는다. 엔티티를 미리 읽어 두면 뒤이은 {@link #findWithLockById}가
	 * 영속성 컨텍스트의 옛 confirm_count를 돌려주므로 잠금 조회 전에는 엔티티를 읽지 않는다.
	 */
	@Query("select l.id from IngredientList l where l.product.id = :productId and l.fingerprint = :fingerprint")
	Optional<Long> findIdByProductIdAndFingerprint(@Param("productId") Long productId,
			@Param("fingerprint") String fingerprint);

	/** 확인 추가: 서로 다른 사용자의 동시 확인이 confirm_count를 덮어쓰지 않게 행을 잠그고 읽는다 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select l from IngredientList l where l.id = :id")
	Optional<IngredientList> findWithLockById(@Param("id") Long id);

	List<IngredientList> findByProductId(Long productId);

	/** 여러 제품의 대표 버전을 한 번에 계산할 때 쓴다 */
	List<IngredientList> findByProductIdIn(Collection<Long> productIds);
}
