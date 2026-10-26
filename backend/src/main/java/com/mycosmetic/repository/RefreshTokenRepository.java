package com.mycosmetic.repository;

import java.time.Instant;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mycosmetic.entity.RefreshToken;

import jakarta.persistence.LockModeType;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, Long> {

	/** 로테이션 중 같은 토큰으로 동시에 갱신하는 요청을 줄 세운다 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<RefreshToken> findByTokenHash(String tokenHash);

	/** 재사용 감지 시 같은 계보의 아직 유효한 토큰을 모두 폐기한다 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update RefreshToken t set t.revokedAt = :now where t.familyId = :familyId and t.revokedAt is null")
	int revokeFamily(@Param("familyId") String familyId, @Param("now") Instant now);

	/** 로그아웃·탈퇴·비밀번호 변경 시 사용자의 모든 토큰을 폐기한다 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("update RefreshToken t set t.revokedAt = :now where t.user.id = :userId and t.revokedAt is null")
	int revokeAllByUserId(@Param("userId") Long userId, @Param("now") Instant now);

	/** 탈퇴 시 사용자의 행을 한 번에 지운다 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from RefreshToken t where t.user.id = :userId")
	int deleteAllByUserId(@Param("userId") Long userId);
}
