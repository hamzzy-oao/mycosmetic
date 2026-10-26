package com.mycosmetic.config;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.runner.ApplicationContextRunner;

class UnsafeSettingsGuardTest {

	private final ApplicationContextRunner runner = new ApplicationContextRunner()
			.withUserConfiguration(UnsafeSettingsGuard.class);

	@Test
	@DisplayName("test·e2e 프로필 없이 app.cookie.secure=false면 컨텍스트가 뜨지 않는다")
	void insecureCookieWithoutTestProfile_fails() {
		runner.withPropertyValues("app.cookie.secure=false")
				.run(context -> {
					assertThat(context).hasFailed();
					assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("app.cookie.secure");
				});
	}

	@Test
	@DisplayName("운영(prod) 프로필에서 app.extraction.client=fake면 컨텍스트가 뜨지 않는다")
	void fakeClientInProd_fails() {
		runner.withPropertyValues("spring.profiles.active=prod", "app.extraction.client=fake")
				.run(context -> {
					assertThat(context).hasFailed();
					assertThat(context.getStartupFailure()).rootCause().hasMessageContaining("app.extraction.client");
				});
	}

	@Test
	@DisplayName("기본값(쿠키 Secure, 실제 Claude)이면 프로필이 없어도 뜬다")
	void defaults_start() {
		runner.run(context -> assertThat(context).hasNotFailed());
	}

	@Test
	@DisplayName("eval 프로필은 실제 Claude client로 뜬다")
	void evalProfileWithRealClient_starts() {
		runner.withPropertyValues("spring.profiles.active=eval", "app.extraction.client=claude")
				.run(context -> assertThat(context).hasNotFailed());
	}

	@Test
	@DisplayName("e2e 프로필은 Secure 없는 쿠키와 가짜 client로 뜬다")
	void e2eProfile_starts() {
		runner.withPropertyValues("spring.profiles.active=e2e", "app.cookie.secure=false", "app.extraction.client=fake")
				.run(context -> assertThat(context).hasNotFailed());
	}

	@Test
	@DisplayName("test 프로필은 가짜 client로 뜬다")
	void testProfile_starts() {
		runner.withPropertyValues("spring.profiles.active=test", "app.extraction.client=fake")
				.run(context -> assertThat(context).hasNotFailed());
	}
}
