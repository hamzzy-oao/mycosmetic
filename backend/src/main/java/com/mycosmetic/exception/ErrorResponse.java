package com.mycosmetic.exception;

import org.slf4j.MDC;

import com.mycosmetic.config.logging.TraceIdFilter;

/**
 * 모든 오류 응답의 형식: { "code", "message", "traceId" }.
 */
public record ErrorResponse(String code, String message, String traceId) {

	/** traceId는 현재 요청의 MDC 값을 쓴다. */
	public static ErrorResponse of(ErrorCode errorCode) {
		return new ErrorResponse(errorCode.name(), errorCode.message(), MDC.get(TraceIdFilter.MDC_KEY));
	}
}
