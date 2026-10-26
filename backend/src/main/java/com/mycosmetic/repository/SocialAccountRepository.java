package com.mycosmetic.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mycosmetic.entity.Provider;
import com.mycosmetic.entity.SocialAccount;

public interface SocialAccountRepository extends JpaRepository<SocialAccount, Long> {

	Optional<SocialAccount> findByProviderAndProviderUserId(Provider provider, String providerUserId);

	Optional<SocialAccount> findByUserIdAndProvider(Long userId, Provider provider);

	/** 탈퇴 시 사용자의 행을 한 번에 지운다 */
	@Modifying(clearAutomatically = true, flushAutomatically = true)
	@Query("delete from SocialAccount a where a.user.id = :userId")
	int deleteAllByUserId(@Param("userId") Long userId);
}
