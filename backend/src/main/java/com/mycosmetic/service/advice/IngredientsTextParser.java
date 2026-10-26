package com.mycosmetic.service.advice;

import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.exception.ErrorCode;

/**
 * 붙여넣은 전성분 글을 성분 이름 목록으로 나눈다(no-ai-features 설계 F 입력 3).
 * 줄바꿈과 쉼표로 나누되, 숫자 사이의 쉼표("1,2-헥산다이올")는 이름의 일부로 둔다. 전각 쉼표도 쉼표로 본다.
 */
final class IngredientsTextParser {

	static final int MAX_ITEMS = 200;

	private static final Pattern SEPARATOR = Pattern.compile("\\R|(?<!\\d)[,\uFF0C]|[,\uFF0C](?!\\d)");

	private IngredientsTextParser() {
	}

	/** 빈 이름은 버린다. 하나도 없으면 INVALID_REQUEST, 200개를 넘으면 ADVICE_TOO_MANY_INGREDIENTS */
	static List<String> parse(String text) {
		List<String> names = Arrays.stream(SEPARATOR.split(text == null ? "" : text))
				.map(String::strip)
				.filter(s -> !s.isEmpty())
				.toList();
		if (names.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST);
		}
		if (names.size() > MAX_ITEMS) {
			throw new BusinessException(ErrorCode.ADVICE_TOO_MANY_INGREDIENTS);
		}
		return names;
	}
}
