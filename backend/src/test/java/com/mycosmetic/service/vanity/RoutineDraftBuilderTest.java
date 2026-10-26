package com.mycosmetic.service.vanity;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mycosmetic.entity.SkinConcern;
import com.mycosmetic.entity.StepType;
import com.mycosmetic.entity.Verdict;
import com.mycosmetic.service.routine.IngredientGroup;
import com.mycosmetic.service.routine.WarningLevel;
import com.mycosmetic.service.vanity.RoutineDraft.DraftStep;
import com.mycosmetic.service.vanity.RoutineDraft.Gap;

/** 보유 제품으로 아침·저녁 루틴 초안(퍼스널 탭 설계 3절 A) */
class RoutineDraftBuilderTest {

	private static DraftItem item(long id, StepType step) {
		return new DraftItem(id, step, false, Verdict.UNRATED, null, Set.of());
	}

	private static DraftItem item(long id, StepType step, IngredientGroup... groups) {
		return new DraftItem(id, step, false, Verdict.UNRATED, null, Set.of(groups));
	}

	private static List<Long> ids(List<DraftStep> steps) {
		return steps.stream().map(DraftStep::shelfItemId).toList();
	}

	private static List<String> roles(RoutineDraft draft) {
		return draft.gaps().stream().map(Gap::role).toList();
	}

	@Test
	@DisplayName("단계 종류 순서로 놓는다(세안 → 토너 → 크림)")
	void orderedByStepType() {
		RoutineDraft draft = RoutineDraftBuilder.build(
				List.of(item(1, StepType.CREAM), item(2, StepType.TONER), item(3, StepType.CLEANSER)), Set.of());

		assertThat(ids(draft.morning())).containsExactly(3L, 2L, 1L);
		assertThat(ids(draft.evening())).containsExactly(3L, 2L, 1L);
	}

	@Test
	@DisplayName("선크림은 아침에만")
	void sunscreen_morningOnly() {
		RoutineDraft draft = RoutineDraftBuilder.build(List.of(item(1, StepType.SUNSCREEN)), Set.of());

		assertThat(ids(draft.morning())).containsExactly(1L);
		assertThat(draft.evening()).isEmpty();
	}

	@Test
	@DisplayName("레티놀·BHA가 든 제품은 저녁에만")
	void sunSensitive_eveningOnly() {
		RoutineDraft draft = RoutineDraftBuilder.build(List.of(item(1, StepType.SERUM, IngredientGroup.RETINOID),
				item(2, StepType.TONER, IngredientGroup.BHA)), Set.of());

		assertThat(draft.morning()).isEmpty();
		assertThat(ids(draft.evening())).containsExactly(2L, 1L);
	}

	@Test
	@DisplayName("같은 단계가 여러 개면 잘맞아요 → 최근 시작 순으로 하나 고르고 나머지는 교대 후보")
	void duplicateStep_goodFirstThenRecent() {
		DraftItem neutralRecent = new DraftItem(1L, StepType.CREAM, false, Verdict.NEUTRAL, LocalDate.of(2026, 10, 1),
				Set.of());
		DraftItem good = new DraftItem(2L, StepType.CREAM, false, Verdict.GOOD, LocalDate.of(2025, 1, 1), Set.of());
		DraftItem older = new DraftItem(3L, StepType.CREAM, false, Verdict.NEUTRAL, LocalDate.of(2026, 1, 1),
				Set.of());

		RoutineDraft draft = RoutineDraftBuilder.build(List.of(neutralRecent, good, older), Set.of());

		assertThat(ids(draft.morning())).containsExactly(2L);
		assertThat(draft.alternates()).singleElement().satisfies(a -> {
			assertThat(a.stepType()).isEqualTo(StepType.CREAM);
			assertThat(a.shelfItemIds()).containsExactly(1L, 3L);
		});
	}

	@Test
	@DisplayName("저녁 전용 제품은 아침 후보가 되지 않아, 같은 단계의 다른 제품이 아침에 들어간다")
	void eveningOnly_doesNotBlockMorning() {
		DraftItem retinol = new DraftItem(1L, StepType.SERUM, false, Verdict.GOOD, null,
				Set.of(IngredientGroup.RETINOID));
		DraftItem vitamin = new DraftItem(2L, StepType.SERUM, false, Verdict.UNRATED, null, Set.of());

		RoutineDraft draft = RoutineDraftBuilder.build(List.of(retinol, vitamin), Set.of());

		assertThat(ids(draft.morning())).containsExactly(2L);
		assertThat(ids(draft.evening())).containsExactly(1L);
		assertThat(draft.alternates()).isEmpty();
	}

	@Test
	@DisplayName("빈 자리는 꼭 필요한 단계(세안·보습·아침 선크림)만 알린다")
	void gaps_essentialOnly() {
		RoutineDraft draft = RoutineDraftBuilder.build(List.of(item(1, StepType.TONER)), Set.of());

		assertThat(roles(draft)).containsExactly("CLEANSER", "MOISTURIZER", "SUNSCREEN");
		assertThat(draft.gaps()).allMatch(g -> g.level().equals("ESSENTIAL") && !g.message().isBlank());
		assertThat(draft.gaps().get(2).ingredientHints()).containsExactly("자외선 차단 제품");
		assertThat(draft.gaps().get(0).contentId()).isEqualTo("tip-cleanser");
	}

	@Test
	@DisplayName("로션만 있어도 보습 단계는 채워진 것으로 본다")
	void moisturizer_lotionOrCream() {
		RoutineDraft draft = RoutineDraftBuilder.build(
				List.of(item(1, StepType.CLEANSER), item(2, StepType.LOTION), item(3, StepType.SUNSCREEN)), Set.of());

		assertThat(draft.gaps()).isEmpty();
	}

	@Test
	@DisplayName("보습 빈 자리에 내 고민에 맞는 성분을 안내한다")
	void gap_ingredientHints_byConcern() {
		RoutineDraft draft = RoutineDraftBuilder.build(List.of(), Set.of(SkinConcern.DRYNESS, SkinConcern.SENSITIVITY));

		Gap moisturizer = draft.gaps().stream().filter(g -> g.role().equals("MOISTURIZER")).findFirst().orElseThrow();
		assertThat(moisturizer.ingredientHints()).contains("세라마이드", "히알루론산", "판테놀");
	}

	@Test
	@DisplayName("초안에 기존 루틴 충돌 규칙을 적용한다(저녁에 레티놀 + AHA는 경고)")
	void warnings_fromRoutineConflictRules() {
		RoutineDraft draft = RoutineDraftBuilder.build(List.of(item(1, StepType.SERUM, IngredientGroup.RETINOID),
				item(2, StepType.TONER, IngredientGroup.AHA)), Set.of());

		assertThat(draft.warnings()).anySatisfy(w -> {
			assertThat(w.level()).isEqualTo(WarningLevel.WARNING);
			assertThat(w.code()).isEqualTo("RETINOID_AHA");
		});
	}

	@Test
	@DisplayName("마스크·기타는 매일 루틴에 넣지 않는다")
	void maskAndEtc_excluded() {
		RoutineDraft draft = RoutineDraftBuilder.build(List.of(item(1, StepType.MASK), item(2, StepType.ETC)), Set.of());

		assertThat(draft.morning()).isEmpty();
		assertThat(draft.evening()).isEmpty();
	}

	@Test
	@DisplayName("아무것도 없으면 빈 초안 + 꼭 필요한 빈 자리 3개")
	void empty() {
		RoutineDraft draft = RoutineDraftBuilder.build(new ArrayList<>(), Set.of());

		assertThat(draft.morning()).isEmpty();
		assertThat(draft.evening()).isEmpty();
		assertThat(draft.gaps()).hasSize(3);
		assertThat(draft.warnings()).isEmpty();
	}
}
