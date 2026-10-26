package com.mycosmetic.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.mycosmetic.entity.Provider;
import com.mycosmetic.entity.SocialAccount;
import com.mycosmetic.entity.User;
import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.repository.SocialAccountRepository;
import com.mycosmetic.repository.UserRepository;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.TestAuthHelper;

/** 중복 불가 제약과 잠금이 걸리므로 실제 MySQL(mycosmetic_test)로 돌린다 */
class SocialAccountServiceTest extends IntegrationTestSupport {

	@Autowired
	SocialAccountService socialAccountService;

	@Autowired
	SocialAccountRepository socialAccountRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	TestAuthHelper auth;

	private static String kakaoId() {
		return "k-" + UUID.randomUUID();
	}

	private SocialAccount account(String kakaoId) {
		return socialAccountRepository.findByProviderAndProviderUserId(Provider.KAKAO, kakaoId).orElseThrow();
	}

	@Test
	@DisplayName("처음 보는 카카오 ID는 새 사용자를 만들고 닉네임을 카카오 프로필로")
	void newKakaoId_createsUser() {
		String kakaoId = kakaoId();

		Long userId = socialAccountService.loginOrRegister(kakaoId, "카카오닉", "k@kakao.com", null);

		User user = userRepository.findById(userId).orElseThrow();
		assertThat(user.getNickname()).isEqualTo("카카오닉");
		assertThat(user.getPasswordHash()).isNull();
		// 이메일은 social_accounts에만 저장한다(설계 5.3)
		assertThat(user.getEmail()).isNull();
		assertThat(account(kakaoId).getEmail()).isEqualTo("k@kakao.com");
		assertThat(account(kakaoId).getUser().getId()).isEqualTo(userId);
	}

	@Test
	@DisplayName("이미 연결된 카카오 ID는 그 사용자로 로그인")
	void linkedKakaoId_logsInSameUser() {
		String kakaoId = kakaoId();
		Long first = socialAccountService.loginOrRegister(kakaoId, "닉", null, null);
		long users = userRepository.count();

		Long second = socialAccountService.loginOrRegister(kakaoId, "바뀐닉", null, null);

		assertThat(second).isEqualTo(first);
		assertThat(userRepository.count()).isEqualTo(users);
	}

	@Test
	@DisplayName("카카오 이메일이 기존 이메일 회원과 같아도 합치지 않고 새 사용자")
	void sameEmail_isNotMerged() {
		User emailUser = auth.createUser(TestAuthHelper.uniqueEmail());

		Long kakaoUserId = socialAccountService.loginOrRegister(kakaoId(), "닉", emailUser.getEmail(), null);

		assertThat(kakaoUserId).isNotEqualTo(emailUser.getId());
		assertThat(userRepository.findById(emailUser.getId())).isPresent();
		assertThat(socialAccountRepository.findByUserIdAndProvider(emailUser.getId(), Provider.KAKAO)).isEmpty();
	}

	@Test
	@DisplayName("연결 요청이면 현재 계정에 연결")
	void linkRequest_linksToCurrentUser() {
		User user = auth.createUser(TestAuthHelper.uniqueEmail());
		String kakaoId = kakaoId();
		long users = userRepository.count();

		Long userId = socialAccountService.loginOrRegister(kakaoId, "닉", null, user.getId());

		assertThat(userId).isEqualTo(user.getId());
		assertThat(account(kakaoId).getUser().getId()).isEqualTo(user.getId());
		assertThat(userRepository.count()).isEqualTo(users);
	}

	@Test
	@DisplayName("다른 회원에 이미 연결된 카카오 ID로 연결 요청하면 SOCIAL_ALREADY_LINKED")
	void kakaoLinkedToOther_isRejected() {
		String kakaoId = kakaoId();
		Long owner = socialAccountService.loginOrRegister(kakaoId, "닉", null, null);
		User other = auth.createUser(TestAuthHelper.uniqueEmail());

		assertThatThrownBy(() -> socialAccountService.loginOrRegister(kakaoId, "닉", null, other.getId()))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.SOCIAL_ALREADY_LINKED);
		assertThat(account(kakaoId).getUser().getId()).isEqualTo(owner);
	}

	@Test
	@DisplayName("이미 다른 카카오 계정이 연결된 회원이 또 연결하면 SOCIAL_ALREADY_LINKED")
	void userAlreadyHasKakao_isRejected() {
		User user = auth.createUser(TestAuthHelper.uniqueEmail());
		socialAccountService.loginOrRegister(kakaoId(), "닉", null, user.getId());
		String secondKakaoId = kakaoId();

		assertThatThrownBy(() -> socialAccountService.loginOrRegister(secondKakaoId, "닉", null, user.getId()))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.SOCIAL_ALREADY_LINKED);
		assertThat(socialAccountRepository.findByProviderAndProviderUserId(Provider.KAKAO, secondKakaoId)).isEmpty();
	}

	@Test
	@DisplayName("이미 연결된 카카오 ID를 같은 회원이 다시 연결하면 그대로 성공")
	void relinkSameUser_isNoop() {
		User user = auth.createUser(TestAuthHelper.uniqueEmail());
		String kakaoId = kakaoId();
		socialAccountService.loginOrRegister(kakaoId, "닉", null, user.getId());

		assertThat(socialAccountService.loginOrRegister(kakaoId, "닉", null, user.getId())).isEqualTo(user.getId());
	}

	@Test
	@DisplayName("비밀번호가 없는 카카오 전용 계정은 해제하면 LAST_LOGIN_METHOD")
	void unlinkKakaoOnly_isRejected() {
		String kakaoId = kakaoId();
		Long userId = socialAccountService.loginOrRegister(kakaoId, "닉", null, null);

		assertThatThrownBy(() -> socialAccountService.unlink(userId))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.LAST_LOGIN_METHOD);
		assertThat(account(kakaoId).getUser().getId()).isEqualTo(userId);
	}

	@Test
	@DisplayName("비밀번호가 있는 회원은 카카오 연결을 해제할 수 있고, 연결이 없으면 아무 일도 없다")
	void unlinkWithPassword_removesLink() {
		User user = auth.createUser(TestAuthHelper.uniqueEmail());
		socialAccountService.loginOrRegister(kakaoId(), "닉", null, user.getId());

		socialAccountService.unlink(user.getId());
		socialAccountService.unlink(user.getId());

		assertThat(socialAccountRepository.findByUserIdAndProvider(user.getId(), Provider.KAKAO)).isEmpty();
	}
}
