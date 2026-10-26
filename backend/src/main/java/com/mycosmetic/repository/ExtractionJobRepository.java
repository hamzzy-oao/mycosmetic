package com.mycosmetic.repository;

import java.time.Instant;
import java.util.Collection;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mycosmetic.entity.ExtractionJob;
import com.mycosmetic.entity.JobStatus;

import jakarta.persistence.LockModeType;

public interface ExtractionJobRepository extends JpaRepository<ExtractionJob, Long> {

	/** 남의 작업은 찾지 못하게 항상 사용자 ID와 함께 조회한다 */
	Optional<ExtractionJob> findByIdAndUserId(Long id, Long userId);

	/** 확인·저장: 같은 job을 동시에 두 번 저장하지 못하게 행을 잠그고 읽는다. 남의 job은 찾지 못한다 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	@Query("select j from ExtractionJob j where j.id = :id and j.user.id = :userId")
	Optional<ExtractionJob> findWithLockByIdAndUserId(@Param("id") Long id, @Param("userId") Long userId);

	/** 하루 한도 계산: since(오늘 0시, Asia/Seoul) 이후 한도에 포함되는 작업 수 */
	long countByUserIdAndCountsTowardLimitTrueAndCreatedAtGreaterThanEqual(Long userId, Instant since);

	/** 사용자당 동시 작업 제한: 이 상태(QUEUED·RUNNING)인 작업 수 */
	long countByUserIdAndStatusIn(Long userId, Collection<JobStatus> statuses);

	/** 탈퇴 시 사용자의 행을 한 번에 지운다 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from ExtractionJob j where j.user.id = :userId")
	int deleteAllByUserId(@Param("userId") Long userId);

	/** 정리(설계 4.4): 확인·저장하지 않았고 cutoff 전에 만든 job을 지운다. 확인한 job은 결과 JSON이 이미 비어 있어 남긴다 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from ExtractionJob j where j.confirmedAt is null and j.createdAt < :cutoff")
	int deleteUnconfirmedCreatedBefore(@Param("cutoff") Instant cutoff);

	/**
	 * 정리: startedBefore 전에 시작해 아직 RUNNING인 job을 FAILED로 바꾸고 한도에서 뺀다.
	 * V4 전에 시작한 행은 started_at이 없으므로 생성 시각으로 판단한다
	 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update ExtractionJob j
			set j.status = com.mycosmetic.entity.JobStatus.FAILED, j.errorCode = :errorCode,
				j.countsTowardLimit = false, j.finishedAt = :now
			where j.status = com.mycosmetic.entity.JobStatus.RUNNING
				and coalesce(j.startedAt, j.createdAt) < :startedBefore""")
	int failRunningStartedBefore(@Param("startedBefore") Instant startedBefore, @Param("errorCode") String errorCode,
			@Param("now") Instant now);

	/** 정리: createdBefore 전에 만들어 아직 QUEUED인 job을 FAILED로 바꾸고 한도에서 뺀다 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("""
			update ExtractionJob j
			set j.status = com.mycosmetic.entity.JobStatus.FAILED, j.errorCode = :errorCode,
				j.countsTowardLimit = false, j.finishedAt = :now
			where j.status = com.mycosmetic.entity.JobStatus.QUEUED and j.createdAt < :createdBefore""")
	int failQueuedCreatedBefore(@Param("createdBefore") Instant createdBefore, @Param("errorCode") String errorCode,
			@Param("now") Instant now);
}
