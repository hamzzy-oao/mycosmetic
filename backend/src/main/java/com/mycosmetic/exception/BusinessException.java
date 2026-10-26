package com.mycosmetic.exception;

/**
 * 예상한 업무 오류. GlobalExceptionHandler가 ErrorCode의 상태·메시지로 응답한다.
 */
public class BusinessException extends RuntimeException {

	private final ErrorCode errorCode;

	public BusinessException(ErrorCode errorCode) {
		super(errorCode.name());
		this.errorCode = errorCode;
	}

	public ErrorCode errorCode() {
		return errorCode;
	}
}
