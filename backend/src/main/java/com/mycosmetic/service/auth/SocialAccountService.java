package com.mycosmetic.service.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.mycosmetic.entity.Provider;
import com.mycosmetic.entity.SocialAccount;
import com.mycosmetic.entity.User;
import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.repository.SocialAccountRepository;
import com.mycosmetic.repository.UserRepository;

/**
 * 카카오 로그인·가입·연결·해제(설계 5.3).
 * - 이미 연결된 카카오 ID → 그 사용자
 * - 연결 요청(로그인한 사용자) → 현재 계정에 연결
 * - 처음 보는 카카오 ID → 새 사용자. 이메일은 social_accounts에만 저장하고, 같은 이메일 회원이 있어도 합치지 않는다
 */
@Service
public class SocialAccountService {

	private static final Logger log = LoggerFactory.getLogger(SocialAccountService.class);

	private final UserRepository userRepository;
	private final SocialAccountRepository socialAccountRepository;
	private final TransactionTemplate transactionTemplate;

	public SocialAccountService(UserRepository userRepository, SocialAccountRepository socialAccountRepository,
			PlatformTransactionManager transactionManager) {
		this.userRepository = userRepository;
		this.socialAccountRepository = socialAccountRepository;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
	}

	/**
	 * 카카오 인증이 끝난 뒤 로그인할 사용자 ID를 돌려준다.
	 * 같은 카카오 ID의 첫 로그인이 동시에 두 번 오면 한쪽이 중복 불가 제약에 걸리므로, 새 트랜잭션에서 한 번 더 조회한다(설계 4.3과 같은 방식).
	 */
	public Long loginOrRegister(String kakaoId, String nickname, String emailOrNull, Long linkUserIdOrNull) {
		try {
			return transactionTemplate.execute(status -> doLoginOrRegister(kakaoId, nickname, emailOrNull,
					linkUserIdOrNull));
		} catch (DataIntegrityViolationException e) {
			log.info("카카오 계정 동시 연결 감지, 다시 조회 linkUserId={}", linkUserIdOrNull);
			return transactionTemplate.execute(status -> doLoginOrRegister(kakaoId, nickname, emailOrNull,
					linkUserIdOrNull));
		}
	}

	private Long doLoginOrRegister(String kakaoId, String nickname, String emailOrNull, Long linkUserIdOrNull) {
		SocialAccount existing = socialAccountRepository.findByProviderAndProviderUserId(Provider.KAKAO, kakaoId)
				.orElse(null);
		if (existing != null) {
			Long ownerId = existing.getUser().getId();
			if (linkUserIdOrNull != null && !linkUserIdOrNull.equals(ownerId)) {
				log.info("카카오 연결 거절 userId={} reason=linkedToOtherUser", linkUserIdOrNull);
				throw new BusinessException(ErrorCode.SOCIAL_ALREADY_LINKED);
			}
			return ownerId;
		}
		if (linkUserIdOrNull != null) {
			// 같은 사용자의 연결 요청을 줄 세워 카카오 계정이 2개 연결되지 않게 한다
			User user = userRepository.findWithLockById(linkUserIdOrNull)
					.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
			if (socialAccountRepository.findByUserIdAndProvider(user.getId(), Provider.KAKAO).isPresent()) {
				log.info("카카오 연결 거절 userId={} reason=alreadyHasKakao", user.getId());
				throw new BusinessException(ErrorCode.SOCIAL_ALREADY_LINKED);
			}
			socialAccountRepository.saveAndFlush(SocialAccount.link(user, Provider.KAKAO, kakaoId, emailOrNull));
			log.info("카카오 연결 userId={}", user.getId());
			return user.getId();
		}
		User user = userRepository.save(User.ofSocial(null, nickname));
		socialAccountRepository.saveAndFlush(SocialAccount.link(user, Provider.KAKAO, kakaoId, emailOrNull));
		log.info("카카오 가입 userId={}", user.getId());
		return user.getId();
	}

	/** 카카오 연결 해제. 연결이 없으면 아무것도 하지 않는다. 비밀번호가 없으면(카카오 전용) 거절한다 */
	@Transactional
	public void unlink(Long userId) {
		User user = userRepository.findWithLockById(userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
		socialAccountRepository.findByUserIdAndProvider(userId, Provider.KAKAO).ifPresent(account -> {
			if (user.getPasswordHash() == null) {
				throw new BusinessException(ErrorCode.LAST_LOGIN_METHOD);
			}
			socialAccountRepository.delete(account);
			log.info("카카오 연결 해제 userId={}", userId);
		});
	}
}
