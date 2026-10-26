package com.mycosmetic.exception;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.core.AuthenticationException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.context.request.ServletWebRequest;
import org.springframework.web.context.request.WebRequest;
import org.springframework.web.servlet.mvc.method.annotation.ResponseEntityExceptionHandler;

import jakarta.validation.ConstraintViolationException;

/**
 * 오류 응답을 만드는 단 한 곳. 응답에는 code·message·traceId만 넣고,
 * 예외 클래스명·스택·SQL 같은 내부 정보는 넣지 않는다.
 * Spring MVC 표준 예외(검증 실패, 없는 경로, 업로드 크기 초과 등)는 부모가 상태를 정하고
 * {@link #handleExceptionInternal}에서 우리 형식으로 바꾼다.
 */
@RestControllerAdvice
public class GlobalExceptionHandler extends ResponseEntityExceptionHandler {

	private static final Logger log = LoggerFactory.getLogger(GlobalExceptionHandler.class);

	@ExceptionHandler(BusinessException.class)
	ResponseEntity<ErrorResponse> handleBusiness(BusinessException ex) {
		ErrorCode code = ex.errorCode();
		if (code.status().is5xxServerError()) {
			log.error("업무 오류 {}", code, ex);
		} else {
			log.info("업무 오류 {}", code);
		}
		return toResponse(code, code.status(), HttpHeaders.EMPTY);
	}

	@ExceptionHandler(ConstraintViolationException.class)
	ResponseEntity<ErrorResponse> handleConstraintViolation(ConstraintViolationException ex) {
		return toResponse(ErrorCode.INVALID_REQUEST, HttpStatus.BAD_REQUEST, HttpHeaders.EMPTY);
	}

	@ExceptionHandler(Exception.class)
	ResponseEntity<ErrorResponse> handleUnexpected(Exception ex) throws Exception {
		// 인증·권한 예외는 Spring Security(ExceptionTranslationFilter)가 401/403으로 처리하도록 그대로 던진다
		if (ex instanceof AccessDeniedException || ex instanceof AuthenticationException) {
			throw ex;
		}
		log.error("예상하지 못한 오류", ex);
		return toResponse(ErrorCode.INTERNAL_ERROR, HttpStatus.INTERNAL_SERVER_ERROR, HttpHeaders.EMPTY);
	}

	@Override
	protected ResponseEntity<Object> handleExceptionInternal(Exception ex, Object body, HttpHeaders headers,
			HttpStatusCode statusCode, WebRequest request) {
		if (request instanceof ServletWebRequest servletRequest && servletRequest.getResponse() != null
				&& servletRequest.getResponse().isCommitted()) {
			return null;
		}
		if (statusCode.is5xxServerError()) {
			log.error("요청 처리 오류", ex);
		}
		return new ResponseEntity<>(ErrorResponse.of(codeFor(statusCode)), jsonHeaders(headers), statusCode);
	}

	/** 표준 예외의 상태를 code로 바꾼다. 405·415처럼 따로 정한 code가 없는 4xx는 상태는 그대로 두고 INVALID_REQUEST. */
	private static ErrorCode codeFor(HttpStatusCode statusCode) {
		if (statusCode.value() == HttpStatus.NOT_FOUND.value()) {
			return ErrorCode.NOT_FOUND;
		}
		if (statusCode.value() == HttpStatus.CONTENT_TOO_LARGE.value()) {
			return ErrorCode.FILE_TOO_LARGE;
		}
		if (statusCode.is4xxClientError()) {
			return ErrorCode.INVALID_REQUEST;
		}
		return ErrorCode.INTERNAL_ERROR;
	}

	private static ResponseEntity<ErrorResponse> toResponse(ErrorCode code, HttpStatusCode status,
			HttpHeaders headers) {
		return new ResponseEntity<>(ErrorResponse.of(code), jsonHeaders(headers), status);
	}

	private static HttpHeaders jsonHeaders(HttpHeaders source) {
		HttpHeaders headers = new HttpHeaders();
		headers.putAll(source);
		headers.setContentType(MediaType.APPLICATION_JSON);
		return headers;
	}
}
