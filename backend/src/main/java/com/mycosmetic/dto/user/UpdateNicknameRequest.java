package com.mycosmetic.dto.user;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/** PATCH /api/users/me. 가입과 같은 규칙(공백만은 안 됨, 20자 이하). 앞뒤 공백은 지우고 저장한다 */
public record UpdateNicknameRequest(@NotBlank @Size(max = 20) String nickname) {
}
