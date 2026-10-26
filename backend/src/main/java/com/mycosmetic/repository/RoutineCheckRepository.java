package com.mycosmetic.repository;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mycosmetic.entity.RoutineCheck;
import com.mycosmetic.entity.RoutineSlot;

public interface RoutineCheckRepository extends JpaRepository<RoutineCheck, Long> {

	/** 내 기록만: 항상 사용자 ID와 함께 조회한다 */
	@Query("select c from RoutineCheck c where c.user.id = :userId and c.usedOn = :day")
	List<RoutineCheck> findByUserIdAndUsedOn(@Param("userId") Long userId, @Param("day") LocalDate day);

	/** 기간 안의 내 기록(양 끝 포함). 화장대 항목·제품을 함께 읽는다 */
	@Query("select c from RoutineCheck c join fetch c.shelfItem si join fetch si.product"
			+ " where c.user.id = :userId and c.usedOn between :from and :to")
	List<RoutineCheck> findWithItemByUserIdAndUsedOnBetween(@Param("userId") Long userId,
			@Param("from") LocalDate from, @Param("to") LocalDate to);

	@Modifying(flushAutomatically = true)
	@Query("delete from RoutineCheck c where c.user.id = :userId and c.usedOn = :day and c.slot = :slot"
			+ " and c.shelfItem.id in :itemIds")
	int deleteChecks(@Param("userId") Long userId, @Param("day") LocalDate day, @Param("slot") RoutineSlot slot,
			@Param("itemIds") Collection<Long> itemIds);

	/** 탈퇴 시 사용자의 사용 기록을 한 번에 지운다 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from RoutineCheck c where c.user.id = :userId")
	int deleteAllByUserId(@Param("userId") Long userId);
}
