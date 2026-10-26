package com.mycosmetic.service.advice;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mycosmetic.entity.RoutineSlot;
import com.mycosmetic.entity.StepType;
import com.mycosmetic.service.routine.IngredientGroup;
import com.mycosmetic.service.routine.RoutineConflictRules.OtherRoutine;
import com.mycosmetic.service.routine.StepIngredients;

/** 루틴 충돌 미리보기(no-ai-features 설계 F-5) */
class RoutinePreviewTest {

	private static List<AdviceIngredient> ings(String... names) {
		return Arrays.stream(names).map(n -> AdviceIngredient.of(null, null, n)).toList();
	}

	private static OtherRoutine routine(String name, RoutineSlot slot, StepIngredients... steps) {
		return new OtherRoutine(name, slot, Set.of(), List.of(steps));
	}

	@Test
	@DisplayName("AHA 제품을 아침 루틴(BHA 있음)에 넣으면 AHA+BHA 주의와 자외선 주의를 각각 알린다")
	void morning_ahaBha_andSun() {
		OtherRoutine morning = routine("아침", RoutineSlot.MORNING,
				new StepIngredients(3L, StepType.SERUM, Set.of(IngredientGroup.BHA)));

		List<AdviceReason> reasons = RoutinePreview.check(ings("Glycolic Acid", "정제수"), List.of(morning));

		assertThat(reasons).extracting(AdviceReason::message).anySatisfy(m -> assertThat(m).contains("AHA와 BHA"))
				.anySatisfy(m -> assertThat(m).contains("선크림"));
		assertThat(reasons).allSatisfy(r -> {
			assertThat(r.type()).isEqualTo(ReasonType.ROUTINE_CONFLICT);
			assertThat(r.level()).isEqualTo(AdviceLevel.INFO);
			assertThat(r.ingredients()).containsExactly("Glycolic Acid");
			assertThat(r.relatedShelfItemIds()).doesNotContain(RoutinePreview.TARGET_ID);
		});
	}

	@Test
	@DisplayName("루틴에 원래 있던 경고(대상과 무관)는 알리지 않는다")
	void existingWarningsIgnored() {
		OtherRoutine evening = routine("저녁", RoutineSlot.EVENING,
				new StepIngredients(1L, StepType.SERUM, Set.of(IngredientGroup.RETINOID)),
				new StepIngredients(2L, StepType.TONER, Set.of(IngredientGroup.AHA)));

		assertThat(RoutinePreview.check(ings("나이아신아마이드"), List.of(evening))).isEmpty();
	}

	@Test
	@DisplayName("대상에 성분 그룹이 없으면 아무것도 알리지 않는다")
	void noGroups() {
		OtherRoutine evening = routine("저녁", RoutineSlot.EVENING,
				new StepIngredients(1L, StepType.SERUM, Set.of(IngredientGroup.RETINOID)));

		assertThat(RoutinePreview.check(ings("정제수", "글리세린"), List.of(evening))).isEmpty();
	}

	@Test
	@DisplayName("경고 코드에서 성분 그룹을 읽는다")
	void involvedGroups() {
		assertThat(RoutinePreview.involvedGroups("RETINOID_BENZOYL_PEROXIDE"))
				.containsExactlyInAnyOrder(IngredientGroup.RETINOID, IngredientGroup.BENZOYL_PEROXIDE);
		assertThat(RoutinePreview.involvedGroups("VITAMIN_C_NIACINAMIDE"))
				.containsExactlyInAnyOrder(IngredientGroup.VITAMIN_C, IngredientGroup.NIACINAMIDE);
		assertThat(RoutinePreview.involvedGroups("MORNING_SUN_SENSITIVE"))
				.containsExactlyInAnyOrder(IngredientGroup.RETINOID, IngredientGroup.AHA, IngredientGroup.BHA);
		assertThat(RoutinePreview.involvedGroups("EVENING_SUNSCREEN")).isEmpty();
	}
}
