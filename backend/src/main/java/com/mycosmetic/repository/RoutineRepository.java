package com.mycosmetic.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mycosmetic.entity.Routine;

public interface RoutineRepository extends JpaRepository<Routine, Long> {

	/** 남의 루틴은 찾지 못하게 항상 사용자 ID와 함께 조회한다 */
	Optional<Routine> findByIdAndUserId(Long id, Long userId);

	/** 내 루틴 전부: 단계·화장대 항목·제품을 한 번에 읽는다. 만든 순 */
	@Query("select distinct r from Routine r left join fetch r.steps s left join fetch s.shelfItem si left join fetch si.product"
			+ " where r.user.id = :userId order by r.id")
	List<Routine> findWithStepsByUserId(@Param("userId") Long userId);

	/** 탈퇴 시 사용자의 루틴을 한 번에 지운다(routine_steps는 외래 키 ON DELETE CASCADE) */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from Routine r where r.user.id = :userId")
	int deleteAllByUserId(@Param("userId") Long userId);
}
