package com.mycosmetic.service.advice;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;

import com.mycosmetic.entity.SkinType;
import com.mycosmetic.entity.Verdict;
import com.mycosmetic.service.routine.RoutineConflictRules.OtherRoutine;

/**
 * "이거 사도 돼?" 규칙 판단(no-ai-features 설계 F). 순수 함수라 DB 없이 단위 테스트한다.
 *
 * <ul>
 * <li>AVOID: 1번(안 맞았던 성분)이 2개 이상</li>
 * <li>CAUTION: 1번이 1개, 또는 3번(피부 상태) 중 주의(CAUTION) 수준 규칙에 걸림. 지성·복합성(정보 수준)은 판정에 넣지 않는다</li>
 * <li>UNKNOWN: 주의할 것이 없고 평가 기록(GOOD·BAD)이 2개 미만</li>
 * <li>GOOD_FIT: 주의할 것이 없고 2번(잘 맞았던 제품과 겹침) 비율이 50% 이상</li>
 * <li>그 밖(기록은 있지만 겹침이 50% 미만)은 근거가 약해 UNKNOWN</li>
 * </ul>
 * 성분 비교 키는 {@link AdviceIngredient#token()}이고, 1·2번은 흔한 기본 성분({@link CommonIngredients})을 뺀다.
 */
public final class AdviceRules {

	/** 1번: 안 맞았던 제품 몇 개 이상에 공통으로 있어야 하는가 */
	static final int MIN_BAD_ITEMS = 2;
	/** AVOID가 되는 1번 성분 수 */
	static final int AVOID_BAD_INGREDIENTS = 2;
	/** 판단에 필요한 평가 기록(GOOD·BAD) 수 */
	static final int MIN_RECORDS = 2;
	/** GOOD_FIT이 되는 2번 비율 */
	static final double GOOD_FIT_RATIO = 0.5;
	/** 4번: 비슷한 제품으로 보는 성분 겹침 비율 */
	static final double SIMILAR_RATIO = 0.8;

	/**
	 * 판단 대상.
	 *
	 * @param productId 제품으로 고른 경우 그 제품 ID(같은 제품이 화장대에 있는지 본다). 사진·붙여넣기면 null
	 */
	public record Target(Long productId, List<AdviceIngredient> ingredients) {

		public Target {
			ingredients = List.copyOf(ingredients);
		}
	}

	private AdviceRules() {
	}

	public static AdviceResult evaluate(Target target, List<ShelfEntry> shelf, Set<SkinType> skinTypes,
			List<OtherRoutine> routines) {
		List<AdviceIngredient> ingredients = distinct(target.ingredients());
		List<ShelfEntry> bad = shelf.stream().filter(e -> e.verdict() == Verdict.BAD).toList();
		List<ShelfEntry> good = shelf.stream().filter(e -> e.verdict() == Verdict.GOOD).toList();
		List<AdviceReason> reasons = new ArrayList<>();

		AdviceReason badReason = badIngredients(ingredients, bad, good);
		int badHits = badReason == null ? 0 : badReason.ingredients().size();
		if (badReason != null) {
			reasons.add(badReason);
		}
		double ratio = goodRatio(ingredients, good, reasons);
		List<AdviceReason> skin = SkinCautionRules.check(skinTypes, ingredients);
		reasons.addAll(skin);
		boolean skinCaution = skin.stream().anyMatch(r -> r.level() == AdviceLevel.CAUTION);
		reasons.addAll(sameOrSimilar(target.productId(), ingredients, shelf));
		reasons.addAll(RoutinePreview.check(ingredients, routines));
		boolean fewRecords = bad.size() + good.size() < MIN_RECORDS;
		if (fewRecords) {
			reasons.add(new AdviceReason(ReasonType.NEED_MORE_RECORDS, AdviceLevel.INFO,
					"내 화장대 제품에 잘 맞음·안 맞음 기록이 쌓이면 더 정확해져요.", List.of(), List.of()));
		}

		AdviceVerdict verdict;
		if (badHits >= AVOID_BAD_INGREDIENTS) {
			verdict = AdviceVerdict.AVOID;
		} else if (badHits > 0 || skinCaution) {
			verdict = AdviceVerdict.CAUTION;
		} else if (fewRecords) {
			verdict = AdviceVerdict.UNKNOWN;
		} else if (ratio >= GOOD_FIT_RATIO) {
			verdict = AdviceVerdict.GOOD_FIT;
		} else {
			verdict = AdviceVerdict.UNKNOWN;
		}
		reasons.sort(Comparator.comparingInt(r -> r.level().ordinal()));
		return new AdviceResult(verdict, reasons);
	}

	/** 같은 토큰은 처음 것만 남긴다(전성분 순서 유지) */
	static List<AdviceIngredient> distinct(List<AdviceIngredient> ingredients) {
		Map<String, AdviceIngredient> byToken = new LinkedHashMap<>();
		ingredients.forEach(i -> byToken.putIfAbsent(i.token(), i));
		return List.copyOf(byToken.values());
	}

	/**
	 * 내 화장대 전체에서 안 맞았던 제품 2개 이상에 공통으로 있고 잘 맞았던 제품에는 없는 성분 이름(흔한 기본 성분 제외).
	 * 안 맞았던 제품에 처음 나온 순서. 피부 분석의 "확인해 볼 후보 성분"에 쓴다(퍼스널 탭 설계 1절)
	 */
	public static List<String> badCandidates(List<ShelfEntry> shelf) {
		List<ShelfEntry> bad = shelf.stream().filter(e -> e.verdict() == Verdict.BAD).toList();
		List<ShelfEntry> good = shelf.stream().filter(e -> e.verdict() == Verdict.GOOD).toList();
		BadTally tally = BadTally.of(bad, good);
		Map<String, AdviceIngredient> seen = new LinkedHashMap<>();
		bad.forEach(e -> e.ingredients().forEach(i -> seen.putIfAbsent(i.token(), i)));
		return seen.values().stream().filter(tally::flags).map(AdviceIngredient::name).toList();
	}

	/** 1번 집계: 성분 토큰별 안 맞았던 항목과 잘 맞았던 제품의 토큰 */
	private record BadTally(Map<String, Set<Long>> badItemsByToken, Set<String> goodTokens) {

		static BadTally of(List<ShelfEntry> bad, List<ShelfEntry> good) {
			Map<String, Set<Long>> badItemsByToken = new HashMap<>();
			for (ShelfEntry entry : bad) {
				entry.tokens().forEach(t -> badItemsByToken.computeIfAbsent(t, k -> new LinkedHashSet<>())
						.add(entry.shelfItemId()));
			}
			Set<String> goodTokens = new HashSet<>();
			good.forEach(e -> goodTokens.addAll(e.tokens()));
			return new BadTally(badItemsByToken, goodTokens);
		}

		Set<Long> items(AdviceIngredient ingredient) {
			return badItemsByToken.getOrDefault(ingredient.token(), Set.of());
		}

		/** 안 맞았던 제품 2개 이상에 있고, 잘 맞았던 제품에 없고, 기본 성분이 아님 */
		boolean flags(AdviceIngredient ingredient) {
			return items(ingredient).size() >= MIN_BAD_ITEMS && !goodTokens.contains(ingredient.token())
					&& !CommonIngredients.isCommon(ingredient);
		}
	}

	/** 1번: 안 맞았던 제품 2개 이상에 있고 잘 맞았던 제품에는 없는 성분 중 대상에 있는 것 */
	private static AdviceReason badIngredients(List<AdviceIngredient> target, List<ShelfEntry> bad,
			List<ShelfEntry> good) {
		BadTally tally = BadTally.of(bad, good);
		List<String> hits = new ArrayList<>();
		Set<Long> related = new LinkedHashSet<>();
		for (AdviceIngredient ingredient : target) {
			if (tally.flags(ingredient)) {
				hits.add(ingredient.name());
				related.addAll(tally.items(ingredient));
			}
		}
		if (hits.isEmpty()) {
			return null;
		}
		AdviceLevel level = hits.size() >= AVOID_BAD_INGREDIENTS ? AdviceLevel.WARNING : AdviceLevel.CAUTION;
		return new AdviceReason(ReasonType.BAD_INGREDIENT, level,
				"안 맞았던 제품들에 공통으로 들어 있던 성분이 %d개 있어요.".formatted(hits.size()), hits,
				List.copyOf(related));
	}

	/**
	 * 2번: 대상 성분(기본 성분 제외) 중 잘 맞았던 제품 어디엔가 있는 비율. 잘 맞았던 제품이 없으면 근거 없이 0.
	 * 50% 이상이면 긍정(POSITIVE), 아니면 정보(INFO) 근거를 더한다
	 */
	private static double goodRatio(List<AdviceIngredient> target, List<ShelfEntry> good, List<AdviceReason> reasons) {
		if (good.isEmpty()) {
			return 0;
		}
		Set<String> goodTokens = new HashSet<>();
		good.forEach(e -> goodTokens.addAll(e.tokens()));
		List<AdviceIngredient> counted = target.stream().filter(i -> !CommonIngredients.isCommon(i)).toList();
		List<AdviceIngredient> overlap = counted.stream().filter(i -> goodTokens.contains(i.token())).toList();
		double ratio = counted.isEmpty() ? 0 : (double) overlap.size() / counted.size();
		Set<String> overlapTokens = new HashSet<>();
		overlap.forEach(i -> overlapTokens.add(i.token()));
		List<Long> related = good.stream()
				.filter(e -> e.tokens().stream().anyMatch(overlapTokens::contains))
				.map(ShelfEntry::shelfItemId).toList();
		int percent = (int) Math.round(ratio * 100);
		boolean positive = ratio >= GOOD_FIT_RATIO;
		reasons.add(new AdviceReason(ReasonType.GOOD_OVERLAP, positive ? AdviceLevel.POSITIVE : AdviceLevel.INFO,
				positive ? "잘 맞았던 제품들과 성분이 %d%% 겹쳐요.".formatted(percent)
						: "잘 맞았던 제품들과 겹치는 성분은 %d%%예요.".formatted(percent),
				overlap.stream().map(AdviceIngredient::name).toList(), related));
		return ratio;
	}

	/** 4번: 같은 제품이 이미 있거나, 성분이 80% 이상 겹치는 다른 제품이 있으면 정보 근거 */
	private static List<AdviceReason> sameOrSimilar(Long productId, List<AdviceIngredient> target,
			List<ShelfEntry> shelf) {
		List<AdviceReason> reasons = new ArrayList<>();
		List<Long> same = shelf.stream().filter(e -> productId != null && Objects.equals(e.productId(), productId))
				.map(ShelfEntry::shelfItemId).toList();
		if (!same.isEmpty()) {
			reasons.add(new AdviceReason(ReasonType.ALREADY_ON_SHELF, AdviceLevel.INFO,
					"이미 내 화장대에 있는 제품이에요.", List.of(), same));
		}
		Set<String> targetTokens = new HashSet<>();
		target.forEach(i -> targetTokens.add(i.token()));
		List<Long> similar = new ArrayList<>();
		for (ShelfEntry entry : shelf) {
			if (same.contains(entry.shelfItemId()) || targetTokens.isEmpty()) {
				continue;
			}
			Set<String> tokens = entry.tokens();
			long common = tokens.stream().filter(targetTokens::contains).count();
			if ((double) common / Math.max(tokens.size(), targetTokens.size()) >= SIMILAR_RATIO) {
				similar.add(entry.shelfItemId());
			}
		}
		if (!similar.isEmpty()) {
			reasons.add(new AdviceReason(ReasonType.SIMILAR_PRODUCT, AdviceLevel.INFO,
					"성분이 80% 이상 겹치는 비슷한 제품이 내 화장대에 있어요.", List.of(), similar));
		}
		return reasons;
	}
}
