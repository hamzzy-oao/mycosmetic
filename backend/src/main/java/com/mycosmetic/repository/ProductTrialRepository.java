package com.mycosmetic.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mycosmetic.entity.ProductTrial;

public interface ProductTrialRepository extends JpaRepository<ProductTrial, Long> {

	/** 남의 테스트는 찾지 못하게 항상 사용자 ID와 함께 조회한다 */
	Optional<ProductTrial> findByIdAndUserId(Long id, Long userId);

	boolean existsByShelfItemId(Long shelfItemId);

	/** 내 테스트 전부: 화장대 항목·제품을 한 번에 읽는다 */
	@Query("select t from ProductTrial t join fetch t.shelfItem s join fetch s.product where t.userId = :userId order by t.startedOn desc, t.id desc")
	List<ProductTrial> findWithProductByUserId(@Param("userId") Long userId);
}
