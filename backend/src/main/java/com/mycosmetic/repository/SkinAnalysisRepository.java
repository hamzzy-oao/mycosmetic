package com.mycosmetic.repository;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mycosmetic.entity.SkinAnalysis;

public interface SkinAnalysisRepository extends JpaRepository<SkinAnalysis, Long> {

	/** 최신순 10건(같은 시각이면 나중에 만든 것이 앞) */
	List<SkinAnalysis> findTop10ByUserIdOrderByCreatedAtDescIdDesc(Long userId);

	/** 탈퇴 시 사용자의 행을 한 번에 지운다 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from SkinAnalysis a where a.userId = :userId")
	int deleteAllByUserId(@Param("userId") Long userId);
}
