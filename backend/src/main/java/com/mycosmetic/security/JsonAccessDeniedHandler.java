package com.mycosmetic.security;

import java.io.IOException;

import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.csrf.CsrfException;

import com.mycosmetic.exception.ErrorCode;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/** 403을 공통 오류 형식으로 응답한다. CSRF 토큰이 없거나 틀리면 CSRF_INVALID. */
public class JsonAccessDeniedHandler implements AccessDeniedHandler {

	private final JsonMapper jsonMapper;

	public JsonAccessDeniedHandler(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	@Override
	public void handle(HttpServletRequest request, HttpServletResponse response,
			AccessDeniedException accessDeniedException) throws IOException {
		ErrorCode code = accessDeniedException instanceof CsrfException ? ErrorCode.CSRF_INVALID : ErrorCode.FORBIDDEN;
		JsonAuthenticationEntryPoint.write(jsonMapper, response, code);
	}
}
