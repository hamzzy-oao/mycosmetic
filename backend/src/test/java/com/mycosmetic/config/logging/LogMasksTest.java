package com.mycosmetic.config.logging;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

class LogMasksTest {

	@Test
	@DisplayName("이메일을 ab***@gmail.com 형태로 가린다")
	void email_masksLocalPart() {
		assertThat(LogMasks.email("abcdef@gmail.com")).isEqualTo("ab***@gmail.com");
		assertThat(LogMasks.email("ab@gmail.com")).isEqualTo("a***@gmail.com");
		assertThat(LogMasks.email("a@gmail.com")).isEqualTo("a***@gmail.com");
		assertThat(LogMasks.email("not-an-email")).isEqualTo("***");
		assertThat(LogMasks.email(null)).isNull();
	}
}
