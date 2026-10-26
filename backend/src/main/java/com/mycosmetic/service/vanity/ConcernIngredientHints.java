package com.mycosmetic.service.vanity;

import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mycosmetic.entity.SkinConcern;

/** 피부 고민별로 찾아볼 성분(코드 상수, 퍼스널 탭 설계 3절 기본값). 효능을 보장하지 않는 안내다 */
public final class ConcernIngredientHints {

	static final Map<SkinConcern, List<String>> HINTS = Map.of(
			SkinConcern.DRYNESS, List.of("세라마이드", "히알루론산", "글리세린"),
			SkinConcern.OILINESS, List.of("나이아신아마이드"),
			SkinConcern.PORES, List.of("살리실릭애씨드(BHA)"),
			SkinConcern.WRINKLES, List.of("레티놀", "펩타이드"),
			SkinConcern.PIGMENTATION, List.of("나이아신아마이드", "비타민C"),
			SkinConcern.SENSITIVITY, List.of("판테놀", "마데카소사이드"),
			SkinConcern.ACNE, List.of("살리실릭애씨드(BHA)", "티트리"));

	/** 고민이 없을 때 보습 안내 */
	static final List<String> DEFAULT_MOISTURIZER = List.of("세라마이드", "글리세린");

	private ConcernIngredientHints() {
	}

	/** 고민 순서(enum 선언 순서)대로, 같은 성분은 한 번 */
	public static List<String> forConcerns(Set<SkinConcern> concerns) {
		Set<String> hints = new LinkedHashSet<>();
		for (SkinConcern concern : SkinConcern.values()) {
			if (concerns.contains(concern)) {
				hints.addAll(HINTS.get(concern));
			}
		}
		return List.copyOf(hints);
	}
}
