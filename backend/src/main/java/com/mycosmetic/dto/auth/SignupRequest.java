package com.mycosmetic.dto.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/** 이메일 가입. 비밀번호는 8자 이상, 영문과 숫자를 포함한다. */
public record SignupRequest(
		@NotBlank @Email @Size(max = 255) String email,
		@NotBlank @Pattern(regexp = "^(?=.*[A-Za-z])(?=.*\\d).{8,}$") @Size(max = 72) String password,
		@NotBlank @Size(max = 20) String nickname) {
}
