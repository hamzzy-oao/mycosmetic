package com.mycosmetic.dto.user;

/** 내 정보. hasPassword는 이메일 로그인 가능 여부, kakaoLinked는 카카오 연결 여부. */
public record MeResponse(Long id, String email, String nickname, boolean hasPassword, boolean kakaoLinked,
		com.mycosmetic.entity.UserRole role) {
	public MeResponse(Long id, String email, String nickname, boolean hasPassword, boolean kakaoLinked) {
		this(id, email, nickname, hasPassword, kakaoLinked, com.mycosmetic.entity.UserRole.USER);
	}
}
