package com.mycosmetic.service.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.spy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;

import com.mycosmetic.dto.auth.LoginRequest;
import com.mycosmetic.dto.auth.SignupRequest;
import com.mycosmetic.entity.User;
import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.repository.UserRepository;

class AuthServiceTest {

	static final String EMAIL = "abcdef@test.com";
	static final String PASSWORD = "password123";

	final Instant now = Instant.parse("2026-10-22T00:00:00Z");
	UserRepository userRepository;
	PasswordEncoder passwordEncoder;
	AuthService authService;

	@BeforeEach
	void setUp() {
		userRepository = mock(UserRepository.class);
		passwordEncoder = spy(new BCryptPasswordEncoder(4));
		authService = new AuthService(userRepository, passwordEncoder, new LoginAttemptPolicy(),
				Clock.fixed(now, ZoneOffset.UTC));
	}

	private User savedUser(Long id) {
		User user = User.ofEmail(EMAIL, passwordEncoder.encode(PASSWORD), "테스터");
		ReflectionTestUtils.setField(user, "id", id);
		when(userRepository.findIdByEmail(EMAIL)).thenReturn(Optional.of(id));
		when(userRepository.findWithLockById(id)).thenReturn(Optional.of(user));
		return user;
	}

	private static BusinessException catchBusiness(Runnable action) {
		try {
			action.run();
		} catch (BusinessException e) {
			return e;
		}
		throw new AssertionError("BusinessException이 나지 않았다");
	}

	@Test
	@DisplayName("비밀번호를 BCrypt로 저장한다")
	void signup_storesBcryptHash() {
		authService.signup(new SignupRequest(EMAIL, PASSWORD, "테스터"));

		ArgumentCaptor<User> captor = ArgumentCaptor.forClass(User.class);
		verify(userRepository).saveAndFlush(captor.capture());
		User saved = captor.getValue();
		assertThat(saved.getPasswordHash()).isNotEqualTo(PASSWORD).startsWith("$2");
		assertThat(passwordEncoder.matches(PASSWORD, saved.getPasswordHash())).isTrue();
		assertThat(saved.getEmail()).isEqualTo(EMAIL);
	}

	@Test
	@DisplayName("같은 이메일로 다시 가입하면 EMAIL_DUPLICATE")
	void signup_duplicateEmail() {
		when(userRepository.existsByEmail(EMAIL)).thenReturn(true);

		assertThatThrownBy(() -> authService.signup(new SignupRequest(EMAIL, PASSWORD, "테스터")))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.EMAIL_DUPLICATE);
		verify(userRepository, never()).saveAndFlush(any());
	}

	@Test
	@DisplayName("동시에 같은 이메일로 가입해 중복 제약에 걸려도 EMAIL_DUPLICATE")
	void signup_uniqueViolation_isDuplicate() {
		when(userRepository.saveAndFlush(any())).thenThrow(new DataIntegrityViolationException("uk_users_email"));

		assertThatThrownBy(() -> authService.signup(new SignupRequest(EMAIL, PASSWORD, "테스터")))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.EMAIL_DUPLICATE);
	}

	@Test
	@DisplayName("BCrypt 한도(72바이트)를 넘는 비밀번호는 INVALID_REQUEST")
	void signup_tooLongPassword() {
		String longPassword = "a1" + "가".repeat(30);

		assertThatThrownBy(() -> authService.signup(new SignupRequest(EMAIL, longPassword, "테스터")))
				.isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.INVALID_REQUEST);
	}

	@Test
	@DisplayName("없는 이메일과 틀린 비밀번호는 같은 INVALID_CREDENTIALS")
	void login_unknownEmailAndWrongPassword_sameError() {
		when(userRepository.findIdByEmail("nobody@test.com")).thenReturn(Optional.empty());
		BusinessException unknown = catchBusiness(
				() -> authService.login(new LoginRequest("nobody@test.com", PASSWORD)));
		// 가입 여부가 응답 시간으로 드러나지 않게 없는 이메일도 BCrypt를 한 번 돌린다
		verify(passwordEncoder).matches(eq(PASSWORD), anyString());

		savedUser(1L);
		BusinessException wrong = catchBusiness(() -> authService.login(new LoginRequest(EMAIL, "wrong1234")));

		assertThat(unknown.errorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
		assertThat(wrong.errorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
	}

	@Test
	@DisplayName("틀린 비밀번호는 실패 횟수를 늘린다")
	void login_wrongPassword_countsFailure() {
		User user = savedUser(1L);

		catchBusiness(() -> authService.login(new LoginRequest(EMAIL, "wrong1234")));

		assertThat(user.getFailedLoginCount()).isEqualTo(1);
	}

	@Test
	@DisplayName("잠긴 계정은 맞는 비밀번호여도 ACCOUNT_LOCKED")
	void login_lockedAccount() {
		User user = savedUser(1L);
		for (int i = 0; i < 5; i++) {
			user.recordLoginFailure(5, java.time.Duration.ofMinutes(10), now);
		}

		BusinessException e = catchBusiness(() -> authService.login(new LoginRequest(EMAIL, PASSWORD)));

		assertThat(e.errorCode()).isEqualTo(ErrorCode.ACCOUNT_LOCKED);
		assertThat(user.getFailedLoginCount()).isEqualTo(5);
	}

	@Test
	@DisplayName("맞는 비밀번호면 userId를 돌려주고 실패 횟수를 0으로")
	void login_success() {
		User user = savedUser(7L);
		user.recordLoginFailure(5, java.time.Duration.ofMinutes(10), now);

		Long userId = authService.login(new LoginRequest(EMAIL, PASSWORD));

		assertThat(userId).isEqualTo(7L);
		assertThat(user.getFailedLoginCount()).isZero();
	}

	@Test
	@DisplayName("비밀번호가 없는 소셜 전용 계정은 INVALID_CREDENTIALS")
	void login_socialOnlyAccount() {
		User user = User.ofSocial(EMAIL, "카카오");
		ReflectionTestUtils.setField(user, "id", 3L);
		when(userRepository.findIdByEmail(EMAIL)).thenReturn(Optional.of(3L));
		when(userRepository.findWithLockById(3L)).thenReturn(Optional.of(user));

		BusinessException e = catchBusiness(() -> authService.login(new LoginRequest(EMAIL, PASSWORD)));

		assertThat(e.errorCode()).isEqualTo(ErrorCode.INVALID_CREDENTIALS);
	}
}
