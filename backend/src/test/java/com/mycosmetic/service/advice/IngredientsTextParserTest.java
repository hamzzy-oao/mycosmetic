package com.mycosmetic.service.advice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.Collections;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.exception.ErrorCode;

/** 붙여넣은 전성분 나누기 */
class IngredientsTextParserTest {

	@Test
	@DisplayName("쉼표·전각 쉼표·줄바꿈으로 나누고 앞뒤 공백과 빈 이름을 버린다")
	void split() {
		assertThat(IngredientsTextParser.parse(" 정제수, 글리세린\n나이아신아마이드，향료\r\n\n,, 레티놀 "))
				.containsExactly("정제수", "글리세린", "나이아신아마이드", "향료", "레티놀");
	}

	@Test
	@DisplayName("숫자 사이 쉼표(1,2-헥산다이올)는 이름의 일부로 둔다")
	void keepsDigitComma() {
		assertThat(IngredientsTextParser.parse("정제수,1,2-헥산다이올, 2,4-다이클로로벤질알코올"))
				.containsExactly("정제수", "1,2-헥산다이올", "2,4-다이클로로벤질알코올");
	}

	@Test
	@DisplayName("이름이 하나도 없으면 INVALID_REQUEST")
	void empty() {
		assertThatThrownBy(() -> IngredientsTextParser.parse(" , \n "))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INVALID_REQUEST));
		assertThatThrownBy(() -> IngredientsTextParser.parse(null)).isInstanceOf(BusinessException.class);
	}

	@Test
	@DisplayName("200개까지는 되고 201개면 ADVICE_TOO_MANY_INGREDIENTS")
	void limit() {
		assertThat(IngredientsTextParser.parse(String.join(",", Collections.nCopies(200, "a")))).hasSize(200);
		assertThatThrownBy(() -> IngredientsTextParser.parse(String.join(",", Collections.nCopies(201, "a"))))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.ADVICE_TOO_MANY_INGREDIENTS));
	}
}
