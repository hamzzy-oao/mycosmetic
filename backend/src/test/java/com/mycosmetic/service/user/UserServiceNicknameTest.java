package com.mycosmetic.service.user;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.mycosmetic.dto.user.MeResponse;
import com.mycosmetic.entity.User;
import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.repository.UserRepository;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.TestAuthHelper;

/** 닉네임 변경 */
class UserServiceNicknameTest extends IntegrationTestSupport {

	@Autowired
	UserService userService;

	@Autowired
	UserRepository userRepository;

	@Autowired
	TestAuthHelper auth;

	@Test
	@DisplayName("앞뒤 공백을 지워 저장하고 바뀐 내 정보를 돌려준다")
	void changes() {
		User user = auth.createUser(TestAuthHelper.uniqueEmail());

		MeResponse me = userService.changeNickname(user.getId(), "  봄웜  ");

		assertThat(me.nickname()).isEqualTo("봄웜");
		assertThat(userRepository.findById(user.getId()).orElseThrow().getNickname()).isEqualTo("봄웜");
	}

	@Test
	@DisplayName("공백뿐이거나 null이면 INVALID_REQUEST, 없는 사용자는 UNAUTHORIZED")
	void rejects() {
		User user = auth.createUser(TestAuthHelper.uniqueEmail());

		assertThatThrownBy(() -> userService.changeNickname(user.getId(), "  "))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
		assertThatThrownBy(() -> userService.changeNickname(user.getId(), null))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
		assertThatThrownBy(() -> userService.changeNickname(Long.MAX_VALUE, "이름"))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.UNAUTHORIZED));
		assertThat(userRepository.findById(user.getId()).orElseThrow().getNickname()).isEqualTo("테스터");
	}
}
