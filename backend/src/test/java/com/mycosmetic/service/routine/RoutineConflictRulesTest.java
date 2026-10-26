package com.mycosmetic.service.routine;

import static com.mycosmetic.service.routine.IngredientGroup.AHA;
import static com.mycosmetic.service.routine.IngredientGroup.BENZOYL_PEROXIDE;
import static com.mycosmetic.service.routine.IngredientGroup.BHA;
import static com.mycosmetic.service.routine.IngredientGroup.NIACINAMIDE;
import static com.mycosmetic.service.routine.IngredientGroup.RETINOID;
import static com.mycosmetic.service.routine.IngredientGroup.VITAMIN_C;
import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.mycosmetic.entity.RoutineSlot;
import com.mycosmetic.entity.StepType;
import com.mycosmetic.service.routine.RoutineConflictRules.OtherRoutine;

class RoutineConflictRulesTest {

	private static StepIngredients step(long id, IngredientGroup... groups) {
		return step(id, StepType.SERUM, groups);
	}

	private static StepIngredients step(long id, StepType type, IngredientGroup... groups) {
		return new StepIngredients(id, type, groups.length == 0 ? Set.of() : EnumSet.of(groups[0], groups));
	}

	private static List<RoutineWarning> evening(StepIngredients... steps) {
		return RoutineConflictRules.check(RoutineSlot.EVENING, Set.of(), List.of(steps), List.of());
	}

	@ParameterizedTest(name = "{0} + {1} → {2} {3}")
	@CsvSource({
			"RETINOID, AHA, WARNING, RETINOID_AHA",
			"RETINOID, BHA, WARNING, RETINOID_BHA",
			"RETINOID, BENZOYL_PEROXIDE, WARNING, RETINOID_BENZOYL_PEROXIDE",
			"RETINOID, VITAMIN_C, CAUTION, RETINOID_VITAMIN_C",
			"AHA, BHA, CAUTION, AHA_BHA",
			"VITAMIN_C, NIACINAMIDE, INFO, VITAMIN_C_NIACINAMIDE" })
	@DisplayName("두 항목이 충돌 조합이면 규칙대로 경고하고 두 항목 ID를 담는다(순서 무관)")
	void pairRules(IngredientGroup a, IngredientGroup b, WarningLevel level, String code) {
		List<RoutineWarning> forward = evening(step(1, a), step(2, b), step(3));
		List<RoutineWarning> backward = evening(step(2, b), step(1, a));

		for (List<RoutineWarning> warnings : List.of(forward, backward)) {
			assertThat(warnings).hasSize(1);
			RoutineWarning w = warnings.get(0);
			assertThat(w.level()).isEqualTo(level);
			assertThat(w.code()).isEqualTo(code);
			assertThat(w.message()).isNotBlank();
			assertThat(w.items()).containsExactlyInAnyOrder(1L, 2L);
		}
	}

	@Test
	@DisplayName("한 제품 안에 두 그룹이 다 있는 것만으로는 경고하지 않는다")
	void sameItemBothGroups_noWarning() {
		assertThat(evening(step(1, RETINOID, AHA))).isEmpty();
	}

	@Test
	@DisplayName("관계없는 조합(나이아신아마이드+AHA)이나 빈 루틴은 경고가 없다")
	void noConflict() {
		assertThat(evening(step(1, NIACINAMIDE), step(2, AHA))).isEmpty();
		assertThat(evening()).isEmpty();
	}

	@Test
	@DisplayName("경고는 WARNING → CAUTION → INFO 순이고, 여러 항목이 엮이면 모두 담는다")
	void sortedByLevel() {
		List<RoutineWarning> warnings = evening(step(1, VITAMIN_C), step(2, NIACINAMIDE), step(3, RETINOID),
				step(4, AHA), step(5, AHA));

		assertThat(warnings).extracting(RoutineWarning::code)
				.containsExactly("RETINOID_AHA", "RETINOID_VITAMIN_C", "VITAMIN_C_NIACINAMIDE");
		assertThat(warnings.get(0).items()).containsExactlyInAnyOrder(3L, 4L, 5L);
	}

	@Test
	@DisplayName("아침 루틴에 레티노이드·AHA·BHA가 있으면 주의(선크림 권장)")
	void morningSunSensitive() {
		for (IngredientGroup g : List.of(RETINOID, AHA, BHA)) {
			List<RoutineWarning> warnings = RoutineConflictRules.check(RoutineSlot.MORNING, Set.of(),
					List.of(step(1, g), step(2, NIACINAMIDE)), List.of());

			assertThat(warnings).singleElement().satisfies(w -> {
				assertThat(w.level()).isEqualTo(WarningLevel.CAUTION);
				assertThat(w.code()).isEqualTo("MORNING_SUN_SENSITIVE");
				assertThat(w.items()).containsExactly(1L);
			});
		}
		assertThat(RoutineConflictRules.check(RoutineSlot.MORNING, Set.of(), List.of(step(1, VITAMIN_C)), List.of()))
				.isEmpty();
	}

	@Test
	@DisplayName("저녁 루틴의 선크림은 정보 안내, 아침 루틴의 선크림은 안내 없음")
	void eveningSunscreen() {
		assertThat(evening(step(7, StepType.SUNSCREEN))).singleElement().satisfies(w -> {
			assertThat(w.level()).isEqualTo(WarningLevel.INFO);
			assertThat(w.code()).isEqualTo("EVENING_SUNSCREEN");
			assertThat(w.items()).containsExactly(7L);
		});
		assertThat(RoutineConflictRules.check(RoutineSlot.MORNING, Set.of(), List.of(step(7, StepType.SUNSCREEN)),
				List.of())).isEmpty();
	}

	@Test
	@DisplayName("같은 요일에 도는 반대 시간대 루틴과 충돌하면 '같은 날' 정보로 알린다")
	void sameDayAcrossRoutines() {
		OtherRoutine morning = new OtherRoutine("아침 각질", RoutineSlot.MORNING, EnumSet.of(DayOfWeek.MONDAY),
				List.of(step(10, AHA)));

		List<RoutineWarning> warnings = RoutineConflictRules.check(RoutineSlot.EVENING,
				EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.FRIDAY), List.of(step(1, RETINOID)), List.of(morning));

		assertThat(warnings).singleElement().satisfies(w -> {
			assertThat(w.level()).isEqualTo(WarningLevel.INFO);
			assertThat(w.code()).isEqualTo("SAME_DAY");
			assertThat(w.message()).contains("아침 각질").contains("레티노이드").contains("AHA");
			assertThat(w.items()).containsExactlyInAnyOrder(1L, 10L);
		});
	}

	@Test
	@DisplayName("요일이 비면 매일이라 겹친다")
	void sameDay_emptyDaysMeansEveryDay() {
		OtherRoutine morning = new OtherRoutine("아침", RoutineSlot.MORNING, Set.of(), List.of(step(10, BHA)));

		assertThat(RoutineConflictRules.check(RoutineSlot.EVENING, EnumSet.of(DayOfWeek.SUNDAY),
				List.of(step(1, RETINOID)), List.of(morning))).extracting(RoutineWarning::code).containsExactly("SAME_DAY");
	}

	@Test
	@DisplayName("요일이 안 겹치거나, 같은 시간대이거나, 분리를 권하는 규칙(레티노이드+비타민C)이면 '같은 날' 안내가 없다")
	void sameDay_notReported() {
		OtherRoutine tuesday = new OtherRoutine("화", RoutineSlot.MORNING, EnumSet.of(DayOfWeek.TUESDAY),
				List.of(step(10, AHA)));
		OtherRoutine otherEvening = new OtherRoutine("저녁2", RoutineSlot.EVENING, Set.of(), List.of(step(11, AHA)));
		OtherRoutine vitaminC = new OtherRoutine("비타민", RoutineSlot.MORNING, Set.of(), List.of(step(12, VITAMIN_C)));
		OtherRoutine sameItem = new OtherRoutine("같은 제품", RoutineSlot.MORNING, Set.of(),
				List.of(step(1, RETINOID, BENZOYL_PEROXIDE)));

		assertThat(RoutineConflictRules.check(RoutineSlot.EVENING, EnumSet.of(DayOfWeek.MONDAY),
				List.of(step(1, RETINOID, BENZOYL_PEROXIDE)), List.of(tuesday, otherEvening, vitaminC, sameItem)))
				.isEmpty();
	}

	@Test
	@DisplayName("overlaps: 비면 매일, 아니면 교집합이 있어야 한다")
	void overlaps() {
		assertThat(RoutineConflictRules.overlaps(Set.of(), EnumSet.of(DayOfWeek.MONDAY))).isTrue();
		assertThat(RoutineConflictRules.overlaps(EnumSet.of(DayOfWeek.MONDAY), Set.of())).isTrue();
		assertThat(RoutineConflictRules.overlaps(EnumSet.of(DayOfWeek.MONDAY, DayOfWeek.TUESDAY),
				EnumSet.of(DayOfWeek.TUESDAY))).isTrue();
		assertThat(RoutineConflictRules.overlaps(EnumSet.of(DayOfWeek.MONDAY), EnumSet.of(DayOfWeek.TUESDAY)))
				.isFalse();
	}
}
