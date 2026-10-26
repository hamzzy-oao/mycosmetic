package com.mycosmetic.config;

import java.io.IOException;
import java.util.List;

import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * React 화면 주소(/shelf/3 등)를 새로고침해도 index.html을 돌려주도록 forward 한다.
 * API·OAuth2·actuator 경로와 확장자가 있는 정적 파일은 그대로 둔다.
 */
@Component
public class SpaForwardFilter extends OncePerRequestFilter {

	private static final List<String> EXCLUDED_PREFIXES = List.of("/api", "/oauth2", "/login/oauth2", "/actuator");

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		if (isScreenRequest(request)) {
			request.getRequestDispatcher("/index.html").forward(request, response);
			return;
		}
		chain.doFilter(request, response);
	}

	private boolean isScreenRequest(HttpServletRequest request) {
		if (!"GET".equals(request.getMethod())) {
			return false;
		}
		String path = request.getRequestURI().substring(request.getContextPath().length());
		if (EXCLUDED_PREFIXES.stream().anyMatch(p -> path.equals(p) || path.startsWith(p + "/"))) {
			return false;
		}
		String lastSegment = path.substring(path.lastIndexOf('/') + 1);
		return !lastSegment.contains(".");
	}
}
