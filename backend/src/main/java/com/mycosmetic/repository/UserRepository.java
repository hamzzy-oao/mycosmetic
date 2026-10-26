package com.mycosmetic.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mycosmetic.entity.User;

import jakarta.persistence.LockModeType;

public interface UserRepository extends JpaRepository<User, Long> {

	Optional<User> findByEmail(String email);

	boolean existsByEmail(String email);

	/**
	 * 로그인에서 id만 먼저 찾는다. 엔티티를 미리 읽어 두면 뒤이은 {@link #findWithLockById}가
	 * 영속성 컨텍스트의 옛 값을 돌려주므로 잠금 조회 전에는 엔티티를 읽지 않는다.
	 */
	@Query("select u.id from User u where u.email = :email")
	Optional<Long> findIdByEmail(@Param("email") String email);

	/** 로그인 실패 횟수, 하루 한도 계산처럼 같은 사용자의 동시 요청을 줄 세울 때 쓴다 */
	@Lock(LockModeType.PESSIMISTIC_WRITE)
	Optional<User> findWithLockById(Long id);
}
