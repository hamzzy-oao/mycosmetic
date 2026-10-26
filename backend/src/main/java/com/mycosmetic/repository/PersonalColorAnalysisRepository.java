package com.mycosmetic.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mycosmetic.entity.PersonalColorAnalysis;

public interface PersonalColorAnalysisRepository extends JpaRepository<PersonalColorAnalysis, Long> {

	/** 가장 최근 1건(같은 시각이면 나중에 만든 것) */
	Optional<PersonalColorAnalysis> findFirstByUserIdOrderByCreatedAtDescIdDesc(Long userId);

	/** 탈퇴 시 사용자의 행을 한 번에 지운다 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from PersonalColorAnalysis a where a.userId = :userId")
	int deleteAllByUserId(@Param("userId") Long userId);
}
