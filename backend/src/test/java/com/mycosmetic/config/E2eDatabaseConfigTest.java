package com.mycosmetic.config;

import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class E2eDatabaseConfigTest {

	@Test
	@DisplayName("E2E 전용·테스트 DB는 지울 수 있다")
	void disposableDatabases_areAllowed() {
		assertThatCode(() -> E2eDatabaseConfig.requireDisposable("mycosmetic_e2e")).doesNotThrowAnyException();
		assertThatCode(() -> E2eDatabaseConfig.requireDisposable("mycosmetic_test")).doesNotThrowAnyException();
	}

	@Test
	@DisplayName("개발·운영 DB나 알 수 없는 DB는 지우지 않고 시작을 막는다")
	void otherDatabases_areRejected() {
		assertThatThrownBy(() -> E2eDatabaseConfig.requireDisposable("mycosmetic"))
				.isInstanceOf(IllegalStateException.class);
		assertThatThrownBy(() -> E2eDatabaseConfig.requireDisposable(null))
				.isInstanceOf(IllegalStateException.class);
	}
}
