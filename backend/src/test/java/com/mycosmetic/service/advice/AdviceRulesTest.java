package com.mycosmetic.service.advice;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.DayOfWeek;
import java.util.Arrays;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.mycosmetic.entity.RoutineSlot;
import com.mycosmetic.entity.SkinType;
import com.mycosmetic.entity.StepType;
import com.mycosmetic.entity.Verdict;
import com.mycosmetic.service.routine.IngredientGroup;
import com.mycosmetic.service.routine.RoutineConflictRules.OtherRoutine;
import com.mycosmetic.service.routine.StepIngredients;

/** "이거 사도 돼?" 규칙(no-ai-features 설계 F) — 근거 종류별, 판정 경계별 */
class AdviceRulesTest {

	private static List<AdviceIngredient> ings(String... names) {
		return Arrays.stream(names).map(n -> AdviceIngredient.of(null, null, n)).toList();
	}

	private static ShelfEntry entry(long id, Verdict verdict, String... names) {
		return new ShelfEntry(id, 1000 + id, verdict, ings(names));
	}

	private static AdviceResult evaluate(List<AdviceIngredient> target, List<ShelfEntry> shelf) {
		return AdviceRules.evaluate(new AdviceRules.Target(null, target), shelf, Set.of(), List.of());
	}

	private static AdviceReason reason(AdviceResult result, ReasonType type) {
		return result.reasons().stream().filter(r -> r.type() == type).findFirst().orElse(null);
	}

	@Test
	@DisplayName("badCandidates: 내 화장대 전체에서 BAD 2개 이상에 공통이고 GOOD·기본 성분이 아닌 성분(처음 나온 순서)")
	void badCandidates() {
		List<ShelfEntry> shelf = List.of(
				entry(1, Verdict.BAD, "정제수", "리모넨", "향료A", "공통"),
				entry(2, Verdict.BAD, "향료A", "리모넨", "공통", "정제수"),
				entry(3, Verdict.GOOD, "공통"),
				entry(4, Verdict.NEUTRAL, "리모넨"));

		assertThat(AdviceRules.badCandidates(shelf)).containsExactly("리모넨", "향료A");
		assertThat(AdviceRules.badCandidates(List.of(entry(1, Verdict.BAD, "리모넨")))).isEmpty();
		assertThat(AdviceRules.badCandidates(List.of())).isEmpty();
	}

	@Nested
	@DisplayName("1. 안 맞았던 성분")
	class BadIngredients {

		@Test
		@DisplayName("BAD 2개에 공통이고 GOOD에 없는 성분이 대상에 1개 있으면 CAUTION 근거와 CAUTION 판정")
		void oneHit_caution() {
			AdviceResult result = evaluate(ings("정제수", "향료A", "새성분"), List.of(
					entry(1, Verdict.BAD, "향료A", "x1"),
					entry(2, Verdict.BAD, "향료A", "x2"),
					entry(3, Verdict.GOOD, "y1")));

			AdviceReason bad = reason(result, ReasonType.BAD_INGREDIENT);
			assertThat(bad.level()).isEqualTo(AdviceLevel.CAUTION);
			assertThat(bad.ingredients()).containsExactly("향료A");
			assertThat(bad.relatedShelfItemIds()).containsExactly(1L, 2L);
			assertThat(result.verdict()).isEqualTo(AdviceVerdict.CAUTION);
		}

		@Test
		@DisplayName("그런 성분이 2개면 WARNING 근거와 AVOID 판정")
		void twoHits_avoid() {
			AdviceResult result = evaluate(ings("a", "b", "c"), List.of(
					entry(1, Verdict.BAD, "a", "b"),
					entry(2, Verdict.BAD, "a", "b"),
					entry(3, Verdict.GOOD, "c")));

			AdviceReason bad = reason(result, ReasonType.BAD_INGREDIENT);
			assertThat(bad.level()).isEqualTo(AdviceLevel.WARNING);
			assertThat(bad.ingredients()).containsExactly("a", "b");
			assertThat(result.verdict()).isEqualTo(AdviceVerdict.AVOID);
			assertThat(result.reasons().get(0).type()).isEqualTo(ReasonType.BAD_INGREDIENT);
		}

		@Test
		@DisplayName("BAD 1개에만 있거나 GOOD에도 있는 성분, 기본 성분은 근거가 아니다")
		void notCounted() {
			AdviceResult result = evaluate(ings("정제수", "Glycerin", "1,2-헥산다이올", "a", "b"), List.of(
					entry(1, Verdict.BAD, "정제수", "glycerin", "1,2-헥산다이올", "a", "b"),
					entry(2, Verdict.BAD, "정제수", "glycerin", "1,2-헥산다이올", "b"),
					entry(3, Verdict.GOOD, "b")));

			assertThat(reason(result, ReasonType.BAD_INGREDIENT)).isNull();
		}

		@Test
		@DisplayName("NEUTRAL·UNRATED 항목은 BAD로 세지 않는다")
		void neutralIgnored() {
			AdviceResult result = evaluate(ings("a"), List.of(
					entry(1, Verdict.BAD, "a"),
					entry(2, Verdict.NEUTRAL, "a"),
					entry(3, Verdict.UNRATED, "a")));

			assertThat(reason(result, ReasonType.BAD_INGREDIENT)).isNull();
		}

		@Test
		@DisplayName("비교 키는 토큰: 매칭된 성분은 원문이 달라도 같은 ID면 같다")
		void tokenByIngredientId() {
			List<AdviceIngredient> target = List.of(AdviceIngredient.of(7L, "레티놀", "Retinol"));
			ShelfEntry b1 = new ShelfEntry(1L, 11L, Verdict.BAD, List.of(AdviceIngredient.of(7L, "레티놀", "레티놀")));
			ShelfEntry b2 = new ShelfEntry(2L, 12L, Verdict.BAD, List.of(AdviceIngredient.of(7L, "레티놀", "retinol ")));

			AdviceReason bad = reason(evaluate(target, List.of(b1, b2)), ReasonType.BAD_INGREDIENT);

			assertThat(bad.ingredients()).containsExactly("레티놀");
		}
	}

	@Nested
	@DisplayName("2. 잘 맞았던 제품과 겹침 · 판정 경계")
	class GoodOverlap {

		@Test
		@DisplayName("겹침 50%(경계)이고 기록 2개 이상이면 POSITIVE 근거와 GOOD_FIT")
		void half_goodFit() {
			AdviceResult result = evaluate(ings("정제수", "a", "b", "c", "d"), List.of(
					entry(1, Verdict.GOOD, "a"),
					entry(2, Verdict.GOOD, "b", "z")));

			AdviceReason overlap = reason(result, ReasonType.GOOD_OVERLAP);
			assertThat(overlap.level()).isEqualTo(AdviceLevel.POSITIVE);
			assertThat(overlap.message()).contains("50%");
			assertThat(overlap.ingredients()).containsExactly("a", "b");
			assertThat(overlap.relatedShelfItemIds()).containsExactly(1L, 2L);
			assertThat(result.verdict()).isEqualTo(AdviceVerdict.GOOD_FIT);
		}

		@Test
		@DisplayName("겹침이 50% 미만이면 INFO 근거이고 판정은 UNKNOWN")
		void belowHalf_unknown() {
			AdviceResult result = evaluate(ings("a", "b", "c"), List.of(
					entry(1, Verdict.GOOD, "a"),
					entry(2, Verdict.BAD, "q")));

			AdviceReason overlap = reason(result, ReasonType.GOOD_OVERLAP);
			assertThat(overlap.level()).isEqualTo(AdviceLevel.INFO);
			assertThat(overlap.message()).contains("33%");
			assertThat(result.verdict()).isEqualTo(AdviceVerdict.UNKNOWN);
			assertThat(reason(result, ReasonType.NEED_MORE_RECORDS)).isNull();
		}

		@Test
		@DisplayName("기본 성분만 겹치면 비율에 넣지 않는다")
		void commonExcluded() {
			AdviceResult result = evaluate(ings("정제수", "글리세린", "a"), List.of(
					entry(1, Verdict.GOOD, "정제수", "글리세린"),
					entry(2, Verdict.GOOD, "b")));

			assertThat(reason(result, ReasonType.GOOD_OVERLAP).message()).contains("0%");
			assertThat(result.verdict()).isEqualTo(AdviceVerdict.UNKNOWN);
		}

		@Test
		@DisplayName("GOOD 항목이 없으면 겹침 근거가 없다")
		void noGood() {
			AdviceResult result = evaluate(ings("a"), List.of(entry(1, Verdict.BAD, "q"), entry(2, Verdict.BAD, "r")));

			assertThat(reason(result, ReasonType.GOOD_OVERLAP)).isNull();
			assertThat(result.verdict()).isEqualTo(AdviceVerdict.UNKNOWN);
		}

		@Test
		@DisplayName("기록이 2개 미만이면 겹침이 높아도 UNKNOWN과 기록 부족 안내")
		void fewRecords_unknown() {
			AdviceResult result = evaluate(ings("a"), List.of(entry(1, Verdict.GOOD, "a"), entry(2, Verdict.NEUTRAL, "a")));

			assertThat(result.verdict()).isEqualTo(AdviceVerdict.UNKNOWN);
			assertThat(reason(result, ReasonType.NEED_MORE_RECORDS).level()).isEqualTo(AdviceLevel.INFO);
		}

		@Test
		@DisplayName("화장대가 비어 있으면 UNKNOWN")
		void emptyShelf() {
			AdviceResult result = evaluate(ings("a"), List.of());

			assertThat(result.verdict()).isEqualTo(AdviceVerdict.UNKNOWN);
			assertThat(result.reasons()).extracting(AdviceReason::type).containsExactly(ReasonType.NEED_MORE_RECORDS);
		}
	}

	@Nested
	@DisplayName("3. 피부 상태")
	class Skin {

		@Test
		@DisplayName("주의 수준 피부 규칙에 걸리면 기록이 적어도 CAUTION")
		void sensitive_caution() {
			AdviceResult result = AdviceRules.evaluate(new AdviceRules.Target(null, ings("정제수", "향료")), List.of(),
					Set.of(SkinType.SENSITIVE), List.of());

			assertThat(result.verdict()).isEqualTo(AdviceVerdict.CAUTION);
			assertThat(reason(result, ReasonType.SKIN_TYPE).ingredients()).containsExactly("향료");
		}

		@Test
		@DisplayName("지성(정보 수준) 규칙만 걸리면 판정에 넣지 않는다")
		void oily_infoOnly() {
			AdviceResult result = AdviceRules.evaluate(new AdviceRules.Target(null, ings("미네랄오일", "a")),
					List.of(entry(1, Verdict.GOOD, "a"), entry(2, Verdict.GOOD, "미네랄오일")),
					Set.of(SkinType.OILY), List.of());

			assertThat(reason(result, ReasonType.SKIN_TYPE).level()).isEqualTo(AdviceLevel.INFO);
			assertThat(result.verdict()).isEqualTo(AdviceVerdict.GOOD_FIT);
		}
	}

	@Nested
	@DisplayName("4. 이미 가진 제품")
	class Similar {

		@Test
		@DisplayName("같은 제품이 화장대에 있으면 ALREADY_ON_SHELF, 성분 80% 이상 겹치는 다른 제품은 SIMILAR_PRODUCT")
		void sameAndSimilar() {
			ShelfEntry same = new ShelfEntry(1L, 77L, Verdict.UNRATED, ings("a", "b"));
			ShelfEntry similar = new ShelfEntry(2L, 78L, Verdict.UNRATED, ings("a", "b", "c", "d", "x"));
			ShelfEntry different = new ShelfEntry(3L, 79L, Verdict.UNRATED, ings("a", "b", "c", "x", "y"));

			AdviceResult result = AdviceRules.evaluate(new AdviceRules.Target(77L, ings("a", "b", "c", "d", "e")),
					List.of(same, similar, different), Set.of(), List.of());

			assertThat(reason(result, ReasonType.ALREADY_ON_SHELF).relatedShelfItemIds()).containsExactly(1L);
			AdviceReason sim = reason(result, ReasonType.SIMILAR_PRODUCT);
			assertThat(sim.level()).isEqualTo(AdviceLevel.INFO);
			assertThat(sim.relatedShelfItemIds()).containsExactly(2L);
		}

		@Test
		@DisplayName("사진·붙여넣기(제품 ID 없음)는 같은 제품 판정을 하지 않는다")
		void noProductId() {
			AdviceResult result = evaluate(ings("q"), List.of(new ShelfEntry(1L, 77L, Verdict.UNRATED, ings("a"))));

			assertThat(reason(result, ReasonType.ALREADY_ON_SHELF)).isNull();
			assertThat(reason(result, ReasonType.SIMILAR_PRODUCT)).isNull();
		}
	}

	@Nested
	@DisplayName("5. 루틴 충돌 미리보기")
	class Routines {

		@Test
		@DisplayName("레티놀 제품을 AHA가 있는 저녁 루틴에 넣으면 경고가 생긴다고 알린다")
		void retinoidIntoAhaRoutine() {
			OtherRoutine evening = new OtherRoutine("저녁", RoutineSlot.EVENING, Set.of(DayOfWeek.MONDAY),
					List.of(new StepIngredients(5L, StepType.TONER, Set.of(IngredientGroup.AHA))));

			AdviceResult result = AdviceRules.evaluate(new AdviceRules.Target(null, ings("정제수", "레티놀")), List.of(),
					Set.of(), List.of(evening));

			AdviceReason conflict = reason(result, ReasonType.ROUTINE_CONFLICT);
			assertThat(conflict.level()).isEqualTo(AdviceLevel.INFO);
			assertThat(conflict.message()).startsWith("'저녁' 루틴에 넣으면:").contains("레티노이드와 AHA");
			assertThat(conflict.ingredients()).containsExactly("레티놀");
			assertThat(conflict.relatedShelfItemIds()).containsExactly(5L);
			assertThat(result.verdict()).isEqualTo(AdviceVerdict.UNKNOWN);
		}
	}

	@Test
	@DisplayName("같은 성분이 두 번 나오면 한 번만 센다")
	void distinctTokens() {
		assertThat(AdviceRules.distinct(ings("a", "A ", "b"))).extracting(AdviceIngredient::name)
				.containsExactly("a", "b");
	}

	@Test
	@DisplayName("근거는 WARNING → CAUTION → POSITIVE → INFO 순")
	void reasonOrder() {
		AdviceResult result = AdviceRules.evaluate(new AdviceRules.Target(null, ings("a", "b", "향료", "c")),
				List.of(entry(1, Verdict.BAD, "a", "b"), entry(2, Verdict.BAD, "a", "b"), entry(3, Verdict.GOOD, "c")),
				Set.of(SkinType.SENSITIVE), List.of());

		assertThat(result.reasons()).extracting(AdviceReason::level).containsExactly(AdviceLevel.WARNING,
				AdviceLevel.CAUTION, AdviceLevel.INFO);
		assertThat(result.verdict()).isEqualTo(AdviceVerdict.AVOID);
	}
}
