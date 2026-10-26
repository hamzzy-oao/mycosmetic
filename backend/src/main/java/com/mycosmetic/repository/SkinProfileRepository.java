package com.mycosmetic.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mycosmetic.entity.SkinProfile;

/** PK가 user_id다. findById(userId)로 조회한다 */
public interface SkinProfileRepository extends JpaRepository<SkinProfile, Long> {

	/** 탈퇴 시 사용자의 행을 한 번에 지운다 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from SkinProfile p where p.userId = :userId")
	int deleteAllByUserId(@Param("userId") Long userId);
}
