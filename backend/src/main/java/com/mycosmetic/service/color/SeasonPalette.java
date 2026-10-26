package com.mycosmetic.service.color;

import java.util.List;
import java.util.Map;

import com.mycosmetic.entity.PersonalColor;

/**
 * 계절별 비교·추천 팔레트(퍼스널 탭 설계 2절). 드레이핑과 화장대 분석의 색 범위 추천이 같이 쓴다.
 * 모든 색은 기존 ShadeClassifier가 그 계절로 판정한다(테스트로 고정).
 */
public final class SeasonPalette {

	static final Map<PersonalColor, List<String>> COLORS = Map.of(
			// 코랄·피치·살구·사프란 — 밝고 따뜻한 색
			PersonalColor.SPRING_WARM, List.of("#FF7F50", "#FFB07C", "#FFA94D", "#F4C430", "#FFB347", "#F7A072",
					"#FFD27F", "#FF9966"),
			// 로즈·라일락 핑크·플럼 — 밝고 부드러운 차가운 색
			PersonalColor.SUMMER_COOL, List.of("#F4A7B9", "#E8A0BF", "#D8A7CA", "#F7CAC9", "#DDA0DD", "#FFB6C1",
					"#E5989B", "#F2B5D4"),
			// 시에나·브라운·올리브·머스터드 — 깊고 차분한 따뜻한 색
			PersonalColor.AUTUMN_WARM, List.of("#A0522D", "#8B4513", "#996515", "#8F7A3E", "#A67B2E", "#7B3F00",
					"#9C5B3B", "#A0612F"),
			// 버건디·푸시아·체리·루비 — 진하고 선명한 차가운 색
			PersonalColor.WINTER_COOL, List.of("#800020", "#C2185B", "#D2042D", "#B5179E", "#6A0D5F", "#DE3163",
					"#9B111E", "#7B1E5A"));

	private SeasonPalette() {
	}

	public static List<String> of(PersonalColor color) {
		return COLORS.get(color);
	}
}
