package com.mycosmetic.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

/**
 * 테스트·E2E 전용 설정이 운영에 섞이면 시작을 막는다(보안 리뷰).
 * <ul>
 * <li>{@code app.cookie.secure=false}: 토큰 쿠키가 http로도 나간다</li>
 * <li>{@code app.extraction.client=fake}: 실제 사진 대신 저장해 둔 결과를 돌려준다</li>
 * </ul>
 * 두 값은 {@code test}·{@code e2e} 프로필에서만 허용한다. {@code eval} 프로필은 실제 Claude client를 쓰므로 그대로 통과한다.
 */
@Configuration(proxyBeanMethods = false)
public class UnsafeSettingsGuard {

	static final String ALLOWED_PROFILES = "test | e2e";

	public UnsafeSettingsGuard(Environment environment) {
		check(environment);
	}

	static void check(Environment environment) {
		if (environment.matchesProfiles(ALLOWED_PROFILES)) {
			return;
		}
		if (!environment.getProperty("app.cookie.secure", Boolean.class, true)) {
			throw new IllegalStateException("app.cookie.secure=false는 test·e2e 프로필에서만 쓸 수 있다");
		}
		if ("fake".equalsIgnoreCase(environment.getProperty("app.extraction.client", "claude").trim())) {
			throw new IllegalStateException("app.extraction.client=fake는 test·e2e 프로필에서만 쓸 수 있다");
		}
	}
}
