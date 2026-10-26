package com.mycosmetic.exception;

import org.springframework.http.HttpStatus;

/**
 * 오류 응답의 code. HTTP 상태와 사용자가 읽는 한국어 메시지를 함께 둔다.
 * 새 오류는 여기에 추가한다.
 */
public enum ErrorCode {

	INVALID_REQUEST(HttpStatus.BAD_REQUEST, "입력한 내용을 다시 확인해 주세요."),
	UNAUTHORIZED(HttpStatus.UNAUTHORIZED, "로그인이 필요해요."),
	INVALID_CREDENTIALS(HttpStatus.UNAUTHORIZED, "이메일 또는 비밀번호가 맞지 않아요."),
	REFRESH_TOKEN_INVALID(HttpStatus.UNAUTHORIZED, "다시 로그인해 주세요."),
	CSRF_INVALID(HttpStatus.FORBIDDEN, "요청을 확인할 수 없어요. 새로고침 후 다시 시도해 주세요."),
	FORBIDDEN(HttpStatus.FORBIDDEN, "이 요청을 처리할 수 없어요."),
	NOT_FOUND(HttpStatus.NOT_FOUND, "찾을 수 없어요."),
	EMAIL_DUPLICATE(HttpStatus.CONFLICT, "이미 가입된 이메일이에요."),
	SHELF_ITEM_DUPLICATE(HttpStatus.CONFLICT, "이미 내 화장대에 있어요."),
	TRIAL_DUPLICATE(HttpStatus.CONFLICT, "이미 테스트한 제품이에요."),
	PRODUCT_SAME_NAME_EXISTS(HttpStatus.CONFLICT, "같은 이름의 제품이 이미 등록돼 있어요. 그 제품을 고르거나 '다른 제품이에요'를 눌러 주세요."),
	LAST_LOGIN_METHOD(HttpStatus.CONFLICT, "다른 로그인 방법이 없어서 카카오 연결을 해제할 수 없어요."),
	SOCIAL_ALREADY_LINKED(HttpStatus.CONFLICT, "이 카카오 계정은 이미 다른 회원과 연결되어 있어요."),
	FILE_TOO_LARGE(HttpStatus.CONTENT_TOO_LARGE, "사진은 5MB 이하만 올릴 수 있어요."),
	ACCOUNT_LOCKED(HttpStatus.LOCKED, "로그인을 5번 실패해서 10분 동안 잠겼어요."),
	UNSUPPORTED_IMAGE(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "JPEG나 PNG 사진만 올릴 수 있어요."),
	PERSONAL_COLOR_REQUIRED(HttpStatus.BAD_REQUEST, "피부 상태에서 퍼스널컬러를 먼저 골라 주세요."),
	PRODUCT_LINK_UNSUPPORTED(HttpStatus.BAD_REQUEST, "올리브영 상품 상세 링크를 넣어 주세요."),
	PRODUCT_LINK_FETCH_FAILED(HttpStatus.BAD_GATEWAY, "상품 정보를 가져오지 못했어요. 제품명을 직접 입력해 주세요."),
	ADVICE_TOO_MANY_INGREDIENTS(HttpStatus.BAD_REQUEST, "성분은 200개까지 넣을 수 있어요."),
	EXTRACTION_FAILED(HttpStatus.CONFLICT, "성분을 읽지 못한 사진이에요. 다시 찍거나 성분을 붙여넣어 주세요."),
	IMAGE_DIMENSIONS_TOO_LARGE(HttpStatus.BAD_REQUEST, "사진의 가로·세로는 4000px 이하만 올릴 수 있어요."),
	EXTRACTION_DAILY_LIMIT(HttpStatus.TOO_MANY_REQUESTS, "오늘 성분 읽기 횟수(20회)를 모두 사용했어요."),
	EXTRACTION_NOT_READY(HttpStatus.CONFLICT, "아직 성분을 읽는 중이에요."),
	EXTRACTION_ALREADY_CONFIRMED(HttpStatus.CONFLICT, "이미 저장한 결과예요."),
	CONFIRM_CONFLICT(HttpStatus.CONFLICT, "다른 사람이 동시에 저장하고 있어요. 잠시 후 다시 시도해 주세요."),
	EXTRACTION_IN_PROGRESS(HttpStatus.TOO_MANY_REQUESTS, "이미 분석 중인 사진이 있어요. 끝난 뒤 다시 올려 주세요."),
	TOO_MANY_REQUESTS(HttpStatus.TOO_MANY_REQUESTS, "잠시 후 다시 시도해 주세요."),
	INVALID_SKIN_TYPES(HttpStatus.BAD_REQUEST, "피부 타입은 하나만 고르고, 민감성·트러블성은 함께 고를 수 있어요."),
	INVALID_SKIN_ANSWERS(HttpStatus.BAD_REQUEST, "답변을 다시 확인해 주세요."),
	INVALID_ITEM_TYPE(HttpStatus.BAD_REQUEST, "제품 종류를 다시 골라 주세요."),
	INVALID_COLOR_SAMPLES(HttpStatus.BAD_REQUEST, "색 정보를 다시 보내 주세요."),
	PERSONAL_COLOR_LOW_QUALITY(HttpStatus.UNPROCESSABLE_CONTENT, "사진 상태 때문에 분석하기 어려워요. 안내를 보고 다시 찍어 주세요."),
	AI_BUSY(HttpStatus.SERVICE_UNAVAILABLE, "지금 요청이 많아요. 잠시 후 다시 시도해 주세요."),
	INTERNAL_ERROR(HttpStatus.INTERNAL_SERVER_ERROR, "잠시 문제가 생겼어요. 잠시 후 다시 시도해 주세요.");

	private final HttpStatus status;
	private final String message;

	ErrorCode(HttpStatus status, String message) {
		this.status = status;
		this.message = message;
	}

	public HttpStatus status() {
		return status;
	}

	public String message() {
		return message;
	}
}
