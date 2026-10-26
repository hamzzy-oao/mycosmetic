package com.mycosmetic.dto.auth;

/** 가입 화면의 이메일 중복 확인 결과 */
public record EmailAvailabilityResponse(boolean available) {
}
