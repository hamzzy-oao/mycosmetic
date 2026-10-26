package com.mycosmetic.config.logging;

import java.io.IOException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.spi.LoggingEventBuilder;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.security.authentication.AnonymousAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContext;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 요청이 끝날 때 method, path, status, durationMs, userId(인증된 경우)를 한 줄로 남긴다.
 * 쿼리 문자열·본문·쿠키·헤더는 남기지 않는다.
 * Security 필터보다 앞에서 돌아 401·403으로 끝난 요청도 기록한다.
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE + 1)
public class RequestLoggingFilter extends OncePerRequestFilter {

	private static final Logger log = LoggerFactory.getLogger(RequestLoggingFilter.class);

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		long start = System.nanoTime();
		try {
			chain.doFilter(request, response);
		} finally {
			long durationMs = (System.nanoTime() - start) / 1_000_000;
			LoggingEventBuilder event = log.atInfo()
					.addKeyValue("method", request.getMethod())
					.addKeyValue("path", request.getRequestURI())
					.addKeyValue("status", response.getStatus())
					.addKeyValue("durationMs", durationMs);
			String userId = userId(request);
			if (userId != null) {
				event = event.addKeyValue("userId", userId);
			}
			event.log("요청 완료");
		}
	}

	/**
	 * Security 필터는 끝날 때 SecurityContextHolder를 비우므로, 요청 속성에 저장된 SecurityContext
	 * (RequestAttributeSecurityContextRepository)를 먼저 본다. 인증 필터(Task 8)는 이 저장소에 컨텍스트를 저장해야 한다.
	 */
	private static String userId(HttpServletRequest request) {
		Authentication auth = null;
		if (request.getAttribute(RequestAttributeSecurityContextRepository.DEFAULT_REQUEST_ATTR_NAME)
				instanceof SecurityContext context) {
			auth = context.getAuthentication();
		}
		if (auth == null) {
			auth = SecurityContextHolder.getContext().getAuthentication();
		}
		if (auth == null || !auth.isAuthenticated() || auth instanceof AnonymousAuthenticationToken) {
			return null;
		}
		return auth.getName();
	}
}
