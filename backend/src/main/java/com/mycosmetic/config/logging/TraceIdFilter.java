package com.mycosmetic.config.logging;

import java.io.IOException;
import java.util.concurrent.ThreadLocalRandom;

import org.slf4j.MDC;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * 가장 앞 필터. 요청마다 8자리 16진수 traceId를 만들어 MDC와 응답 헤더 X-Trace-Id에 넣는다.
 * 요청 헤더로 들어온 값은 쓰지 않는다(로그 위조 방지).
 */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
public class TraceIdFilter extends OncePerRequestFilter {

	public static final String MDC_KEY = "traceId";
	public static final String HEADER = "X-Trace-Id";

	@Override
	protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
			throws ServletException, IOException {
		String traceId = "%08x".formatted(ThreadLocalRandom.current().nextInt());
		MDC.put(MDC_KEY, traceId);
		response.setHeader(HEADER, traceId);
		try {
			chain.doFilter(request, response);
		} finally {
			MDC.remove(MDC_KEY);
		}
	}
}
