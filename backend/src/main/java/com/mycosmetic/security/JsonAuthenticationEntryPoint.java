package com.mycosmetic.security;

import java.io.IOException;

import org.springframework.http.MediaType;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.AuthenticationEntryPoint;

import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.exception.ErrorResponse;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/** 인증이 없거나 토큰이 틀리면 401 UNAUTHORIZED를 공통 오류 형식으로 응답한다. 원인은 응답에 넣지 않는다. */
public class JsonAuthenticationEntryPoint implements AuthenticationEntryPoint {

	private final JsonMapper jsonMapper;

	public JsonAuthenticationEntryPoint(JsonMapper jsonMapper) {
		this.jsonMapper = jsonMapper;
	}

	@Override
	public void commence(HttpServletRequest request, HttpServletResponse response,
			AuthenticationException authException) throws IOException {
		write(jsonMapper, response, ErrorCode.UNAUTHORIZED);
	}

	static void write(JsonMapper jsonMapper, HttpServletResponse response, ErrorCode code) throws IOException {
		if (response.isCommitted()) {
			return;
		}
		response.setStatus(code.status().value());
		response.setContentType(MediaType.APPLICATION_JSON_VALUE);
		response.setCharacterEncoding("UTF-8");
		response.getWriter().write(jsonMapper.writeValueAsString(ErrorResponse.of(code)));
	}
}
