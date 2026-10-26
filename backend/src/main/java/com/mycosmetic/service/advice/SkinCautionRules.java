package com.mycosmetic.service.advice;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.mycosmetic.entity.SkinType;

/**
 * 피부 상태별 주의 성분(코드 상수, no-ai-features 설계 F-3 기본값). 순수 함수라 DB 없이 단위 테스트한다.
 * 설계의 한국어 이름에 같은 뜻의 영문 INCI 이름을 더했다. 알코올은 세테아릴알코올 같은 지방 알코올과
 * 구별하려고 이름이 정확히 같을 때만 본다.
 */
final class SkinCautionRules {

	static final Set<String> DRYING_ALCOHOL = Set.of("알코올", "에탄올", "변성알코올", "에틸알코올", "alcohol",
			"ethanol", "alcoholdenat", "ethylalcohol", "sdalcohol");

	/**
	 * 규칙 하나.
	 *
	 * @param types 이 규칙을 적용할 피부 상태(하나라도 있으면 적용)
	 * @param level 지성·복합성은 정보 수준(INFO), 나머지는 주의(CAUTION)
	 */
	record SkinRule(Set<SkinType> types, AdviceLevel level, String message, IngredientKeyword keyword) {
	}

	static final List<SkinRule> RULES = List.of(
			new SkinRule(Set.of(SkinType.SENSITIVE), AdviceLevel.CAUTION,
					"민감성 피부에 자극이 될 수 있는 성분(향료·리모넨·리날룰·알코올·멘톨)이 있어요.",
					new IngredientKeyword(DRYING_ALCOHOL, Set.of("향료", "fragrance", "parfum", "리모넨", "limonene",
							"리날룰", "linalool", "멘톨", "menthol"))),
			new SkinRule(Set.of(SkinType.ACNE_PRONE), AdviceLevel.CAUTION,
					"트러블성 피부에 모공을 막을 수 있는 성분이 있어요.",
					new IngredientKeyword(Set.of(), Set.of("코코넛오일", "coconutoil", "코코스누시페라", "cocosnucifera",
							"이소프로필미리스테이트", "isopropylmyristate", "라우릭애씨드", "lauricacid"))),
			new SkinRule(Set.of(SkinType.DRY, SkinType.DEHYDRATED_OILY), AdviceLevel.CAUTION,
					"건성 피부를 더 건조하게 할 수 있는 알코올(에탄올)이 있어요.",
					new IngredientKeyword(DRYING_ALCOHOL, Set.of())),
			new SkinRule(Set.of(SkinType.OILY, SkinType.COMBINATION, SkinType.DEHYDRATED_OILY), AdviceLevel.INFO,
					"지성·복합성 피부에는 무겁게 느껴질 수 있는 성분(미네랄오일·바셀린)이 있어요.",
					new IngredientKeyword(Set.of(), Set.of("미네랄오일", "mineraloil", "paraffinumliquidum", "바셀린",
							"페트롤라툼", "petrolatum"))));

	private SkinCautionRules() {
	}

	/** 내 피부 상태에 해당하는 규칙마다 걸린 성분이 있으면 근거 하나 */
	static List<AdviceReason> check(Set<SkinType> skinTypes, List<AdviceIngredient> target) {
		List<AdviceReason> reasons = new ArrayList<>();
		for (SkinRule rule : RULES) {
			if (Collections.disjoint(rule.types(), skinTypes)) {
				continue;
			}
			Set<String> hits = new LinkedHashSet<>();
			for (AdviceIngredient ingredient : target) {
				if (rule.keyword().matchesAny(ingredient.names())) {
					hits.add(ingredient.name());
				}
			}
			if (!hits.isEmpty()) {
				reasons.add(new AdviceReason(ReasonType.SKIN_TYPE, rule.level(), rule.message(), List.copyOf(hits),
						List.of()));
			}
		}
		return reasons;
	}
}
