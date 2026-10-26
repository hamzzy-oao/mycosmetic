package com.mycosmetic.service.shelf;

import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import com.mycosmetic.dto.shelf.IngredientInsightsResponse;
import com.mycosmetic.dto.shelf.IngredientInsightsResponse.Insight;
import com.mycosmetic.entity.Verdict;

/**
 * 맞음·안 맞음 평가로 성분을 가려낸다(v-8). AI 없이 횟수로만 센다. 한 제품에서 같은 성분은 한 번만 센다.
 *
 * <ul>
 * <li>의심 성분: 안 맞음 제품 2개 이상, 안 맞음 제품의 절반 이상에 있고, 맞음 제품에는 4개 중 1개 이하로만 있는 성분</li>
 * <li>잘 맞는 성분: 맞음 제품 2개 이상, 맞음 제품의 절반 이상에 있고, 안 맞음 제품에는 없는 성분</li>
 * </ul>
 *
 * 정제수처럼 거의 모든 제품에 있는 성분은 맞음 제품에도 많아 의심 성분에서 빠진다. 그래서 맞음 제품이 하나도 없으면
 * 가려낼 수 없어 결과를 비워 두고 더 평가해 달라고 안내한다.
 */
final class IngredientInsights {

	static final int MIN_BAD = 2;
	static final int MIN_GOOD = 1;
	static final int MAX_RESULTS = 10;

	/** 제품 하나의 평가와 성분. key는 같은 성분을 묶는 값(성분 ID 또는 정규화한 이름), name은 보여줄 이름 */
	record Entry(Verdict verdict, Collection<Ingredient> ingredients) {
	}

	record Ingredient(String key, String name) {
	}

	private IngredientInsights() {
	}

	static IngredientInsightsResponse compute(List<Entry> entries) {
		int goodTotal = 0;
		int badTotal = 0;
		Map<String, String> names = new LinkedHashMap<>();
		Map<String, int[]> counts = new HashMap<>(); // [0]=맞음 제품 수, [1]=안 맞음 제품 수
		for (Entry entry : entries) {
			boolean good = entry.verdict() == Verdict.GOOD;
			if (!good && entry.verdict() != Verdict.BAD) {
				continue;
			}
			if (good) goodTotal++;
			else badTotal++;
			Map<String, String> seen = new LinkedHashMap<>();
			for (Ingredient ingredient : entry.ingredients()) {
				seen.putIfAbsent(ingredient.key(), ingredient.name());
			}
			for (Map.Entry<String, String> e : seen.entrySet()) {
				names.putIfAbsent(e.getKey(), e.getValue());
				counts.computeIfAbsent(e.getKey(), k -> new int[2])[good ? 0 : 1]++;
			}
		}
		List<Insight> rows = counts.entrySet().stream()
				.map(e -> new Insight(names.get(e.getKey()), e.getValue()[1], e.getValue()[0]))
				.sorted(Comparator.comparing(Insight::name))
				.toList();
		if (badTotal < MIN_BAD || goodTotal < MIN_GOOD) {
			return new IngredientInsightsResponse(goodTotal, badTotal, false, List.of(), List.of(), rows);
		}
		List<Insight> suspects = new ArrayList<>();
		List<Insight> friendly = new ArrayList<>();
		for (Map.Entry<String, int[]> e : counts.entrySet()) {
			int inGood = e.getValue()[0];
			int inBad = e.getValue()[1];
			Insight insight = new Insight(names.get(e.getKey()), inBad, inGood);
			if (inBad >= 2 && inBad * 2 >= badTotal && inGood * 4 <= goodTotal) {
				suspects.add(insight);
			} else if (inGood >= 2 && inGood * 2 >= goodTotal && inBad == 0) {
				friendly.add(insight);
			}
		}
		suspects.sort(Comparator.comparingInt(Insight::badCount).reversed().thenComparingInt(Insight::goodCount)
				.thenComparing(Insight::name));
		friendly.sort(Comparator.comparingInt(Insight::goodCount).reversed().thenComparing(Insight::name));
		return new IngredientInsightsResponse(goodTotal, badTotal, true,
				suspects.subList(0, Math.min(MAX_RESULTS, suspects.size())),
				friendly.subList(0, Math.min(MAX_RESULTS, friendly.size())), rows);
	}
}
