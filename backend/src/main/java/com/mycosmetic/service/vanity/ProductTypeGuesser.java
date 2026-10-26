package com.mycosmetic.service.vanity;

import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import com.mycosmetic.entity.MakeupType;
import com.mycosmetic.entity.StepType;

/**
 * 제품 이름으로 스킨케어 단계·색조 종류를 추정한다(퍼스널 탭 설계 3절). 사용자가 저장한 값이 있으면 그 값을 먼저 쓴다.
 * 이름은 공백을 지우고 소문자로 비교하고, 긴 키워드부터 본다(아이크림이 크림보다, 선크림이 크림보다 먼저).
 * "스킨케어"·"스킨푸드"처럼 토너가 아닌 "스킨"은 먼저 지운다. 순수 함수라 DB 없이 단위 테스트한다
 */
public final class ProductTypeGuesser {

	static final List<String> IGNORED_WORDS = List.of("스킨케어", "스킨푸드");

	static final List<Map.Entry<String, StepType>> STEP_KEYWORDS = byLengthDesc(Map.ofEntries(
			Map.entry("클렌징", StepType.CLEANSER), Map.entry("클렌저", StepType.CLEANSER), Map.entry("폼", StepType.CLEANSER),
			Map.entry("토너", StepType.TONER), Map.entry("스킨", StepType.TONER),
			Map.entry("에센스", StepType.ESSENCE), Map.entry("세럼", StepType.SERUM), Map.entry("앰플", StepType.AMPOULE),
			Map.entry("아이크림", StepType.EYE), Map.entry("로션", StepType.LOTION), Map.entry("에멀전", StepType.LOTION),
			Map.entry("크림", StepType.CREAM), Map.entry("선크림", StepType.SUNSCREEN), Map.entry("선스틱", StepType.SUNSCREEN),
			Map.entry("선블록", StepType.SUNSCREEN), Map.entry("마스크", StepType.MASK), Map.entry("팩", StepType.MASK)));

	static final List<Map.Entry<String, MakeupType>> MAKEUP_KEYWORDS = byLengthDesc(Map.ofEntries(
			Map.entry("립스틱", MakeupType.LIP), Map.entry("립", MakeupType.LIP), Map.entry("틴트", MakeupType.LIP),
			Map.entry("글로스", MakeupType.LIP), Map.entry("블러셔", MakeupType.CHEEK), Map.entry("치크", MakeupType.CHEEK),
			Map.entry("섀도", MakeupType.EYE), Map.entry("아이라이너", MakeupType.EYE), Map.entry("마스카라", MakeupType.EYE),
			Map.entry("쿠션", MakeupType.BASE), Map.entry("파운데이션", MakeupType.BASE), Map.entry("컨실러", MakeupType.BASE),
			Map.entry("프라이머", MakeupType.BASE)));

	private ProductTypeGuesser() {
	}

	/** 모르면 null */
	public static StepType guessStep(String productName) {
		return guess(productName, STEP_KEYWORDS);
	}

	/** 모르면 null */
	public static MakeupType guessMakeup(String productName) {
		return guess(productName, MAKEUP_KEYWORDS);
	}

	private static <T> T guess(String productName, List<Map.Entry<String, T>> keywords) {
		if (productName == null || productName.isBlank()) {
			return null;
		}
		String name = productName.replaceAll("\\s+", "").toLowerCase(Locale.ROOT);
		for (String ignored : IGNORED_WORDS) {
			name = name.replace(ignored, " ");
		}
		for (Map.Entry<String, T> keyword : keywords) {
			if (name.contains(keyword.getKey())) {
				return keyword.getValue();
			}
		}
		return null;
	}

	private static <T> List<Map.Entry<String, T>> byLengthDesc(Map<String, T> keywords) {
		return keywords.entrySet().stream()
				.sorted(Comparator.comparingInt((Map.Entry<String, T> e) -> e.getKey().length()).reversed()
						.thenComparing(Map.Entry::getKey))
				.toList();
	}
}
