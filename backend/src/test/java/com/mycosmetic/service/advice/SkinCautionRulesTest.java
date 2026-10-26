package com.mycosmetic.service.advice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mycosmetic.entity.SkinType;

/** 피부 상태 주의 성분(no-ai-features 설계 F-3) */
class SkinCautionRulesTest {

	private static List<AdviceIngredient> ings(String... names) {
		return Arrays.stream(names).map(n -> AdviceIngredient.of(null, null, n)).toList();
	}

	@Test
	@DisplayName("민감성: 향료·리모넨·리날룰·멘톨·에탄올(영문 포함)이 걸리고 세테아릴알코올은 걸리지 않는다")
	void sensitive() {
		List<AdviceReason> reasons = SkinCautionRules.check(Set.of(SkinType.SENSITIVE),
				ings("정제수", "Fragrance(Parfum)", "리모넨", "Linalool", "멘톨", "Alcohol Denat.", "세테아릴알코올"));

		assertThat(reasons).hasSize(1);
		assertThat(reasons.get(0).type()).isEqualTo(ReasonType.SKIN_TYPE);
		assertThat(reasons.get(0).level()).isEqualTo(AdviceLevel.CAUTION);
		assertThat(reasons.get(0).ingredients()).containsExactly("Fragrance(Parfum)", "리모넨", "Linalool", "멘톨",
				"Alcohol Denat.");
	}

	@Test
	@DisplayName("트러블성: 코코넛오일·이소프로필미리스테이트·라우릭애씨드")
	void acneProne() {
		List<AdviceReason> reasons = SkinCautionRules.check(Set.of(SkinType.ACNE_PRONE),
				ings("코코넛오일", "Isopropyl Myristate", "라우릭애씨드", "향료"));

		assertThat(reasons).singleElement().satisfies(r -> {
			assertThat(r.level()).isEqualTo(AdviceLevel.CAUTION);
			assertThat(r.ingredients()).containsExactly("코코넛오일", "Isopropyl Myristate", "라우릭애씨드");
		});
	}

	@Test
	@DisplayName("건성: 에탄올만")
	void dry() {
		List<AdviceReason> reasons = SkinCautionRules.check(Set.of(SkinType.DRY), ings("에탄올", "향료"));

		assertThat(reasons).singleElement().satisfies(r -> {
			assertThat(r.level()).isEqualTo(AdviceLevel.CAUTION);
			assertThat(r.ingredients()).containsExactly("에탄올");
		});
	}

	@Test
	@DisplayName("지성·복합성: 미네랄오일·바셀린은 정보 수준이고 둘 다 골라도 한 번만 알린다")
	void oilyCombination() {
		List<AdviceReason> reasons = SkinCautionRules.check(Set.of(SkinType.OILY, SkinType.COMBINATION),
				ings("Mineral Oil", "바셀린"));

		assertThat(reasons).singleElement().satisfies(r -> {
			assertThat(r.level()).isEqualTo(AdviceLevel.INFO);
			assertThat(r.ingredients()).containsExactly("Mineral Oil", "바셀린");
		});
		assertThat(SkinCautionRules.check(Set.of(SkinType.COMBINATION), ings("페트롤라툼"))).hasSize(1);
	}

	@Test
	@DisplayName("수부지: 건성의 알코올 주의 + 지성의 미네랄오일 정보를 함께 받는다")
	void dehydratedOily() {
		List<AdviceReason> reasons = SkinCautionRules.check(Set.of(SkinType.DEHYDRATED_OILY),
				ings("에탄올", "미네랄오일", "향료"));

		assertThat(reasons).hasSize(2);
		assertThat(reasons).filteredOn(r -> r.level() == AdviceLevel.CAUTION).singleElement()
				.satisfies(r -> assertThat(r.ingredients()).containsExactly("에탄올"));
		assertThat(reasons).filteredOn(r -> r.level() == AdviceLevel.INFO).singleElement()
				.satisfies(r -> assertThat(r.ingredients()).containsExactly("미네랄오일"));
	}

	@Test
	@DisplayName("피부 상태가 없거나 걸리는 성분이 없으면 근거가 없다")
	void none() {
		assertThat(SkinCautionRules.check(Set.of(), ings("향료"))).isEmpty();
		assertThat(SkinCautionRules.check(Set.of(SkinType.SENSITIVE), ings("정제수", "글리세린"))).isEmpty();
	}

	@Test
	@DisplayName("매칭된 표준명으로도 판정한다")
	void standardName() {
		List<AdviceIngredient> target = List.of(AdviceIngredient.of(3L, "리날룰", "LINALOOL?"));

		assertThat(SkinCautionRules.check(Set.of(SkinType.SENSITIVE), target)).singleElement()
				.satisfies(r -> assertThat(r.ingredients()).containsExactly("리날룰"));
	}
}
