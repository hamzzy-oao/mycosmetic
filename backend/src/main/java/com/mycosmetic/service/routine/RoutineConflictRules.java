package com.mycosmetic.service.routine;

import static com.mycosmetic.service.routine.IngredientGroup.AHA;
import static com.mycosmetic.service.routine.IngredientGroup.BENZOYL_PEROXIDE;
import static com.mycosmetic.service.routine.IngredientGroup.BHA;
import static com.mycosmetic.service.routine.IngredientGroup.NIACINAMIDE;
import static com.mycosmetic.service.routine.IngredientGroup.RETINOID;
import static com.mycosmetic.service.routine.IngredientGroup.VITAMIN_C;

import java.time.DayOfWeek;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;

import com.mycosmetic.entity.RoutineSlot;
import com.mycosmetic.entity.StepType;

/**
 * 루틴 충돌 규칙(코드 상수, DB 아님 — no-ai-features 설계 A). 순수 함수라 DB 없이 단위 테스트한다.
 */
public final class RoutineConflictRules {

	/**
	 * 두 그룹 조합 규칙.
	 *
	 * @param sameDay 같은 날 아침·저녁 루틴에 나뉘어 있을 때도 "같은 날" 정보로 알릴지.
	 *                아침·저녁 분리를 권하는 규칙(레티노이드+비타민C)과 정보성 규칙은 알리지 않는다
	 */
	record PairRule(IngredientGroup a, IngredientGroup b, WarningLevel level, String code, String message,
			boolean sameDay) {
	}

	static final List<PairRule> PAIR_RULES = List.of(
			new PairRule(RETINOID, AHA, WarningLevel.WARNING, "RETINOID_AHA",
					"레티노이드와 AHA를 한 루틴에 함께 쓰면 자극이 커질 수 있어요. 다른 루틴으로 나눠 주세요.", true),
			new PairRule(RETINOID, BHA, WarningLevel.WARNING, "RETINOID_BHA",
					"레티노이드와 BHA를 한 루틴에 함께 쓰면 자극이 커질 수 있어요. 다른 루틴으로 나눠 주세요.", true),
			new PairRule(RETINOID, BENZOYL_PEROXIDE, WarningLevel.WARNING, "RETINOID_BENZOYL_PEROXIDE",
					"레티노이드와 벤조일퍼옥사이드는 함께 쓰면 효과가 떨어지고 자극이 커질 수 있어요. 다른 루틴으로 나눠 주세요.", true),
			new PairRule(RETINOID, VITAMIN_C, WarningLevel.CAUTION, "RETINOID_VITAMIN_C",
					"레티노이드와 비타민C는 아침·저녁으로 나눠 쓰는 것을 권해요.", false),
			new PairRule(AHA, BHA, WarningLevel.CAUTION, "AHA_BHA",
					"AHA와 BHA를 함께 쓰면 자극이 생길 수 있어요.", true),
			new PairRule(VITAMIN_C, NIACINAMIDE, WarningLevel.INFO, "VITAMIN_C_NIACINAMIDE",
					"비타민C와 나이아신아마이드를 함께 써요. 대부분 괜찮지만 따가우면 시간을 나눠 보세요.", false));

	/** 아침에 쓰면 자외선에 예민해질 수 있는 그룹(화장대 분석의 루틴 초안도 쓴다) */
	public static final Set<IngredientGroup> SUN_SENSITIVE = Set.of(RETINOID, AHA, BHA);

	/** 같은 사용자의 다른 루틴(같은 날 판정용) */
	public record OtherRoutine(String name, RoutineSlot slot, Set<DayOfWeek> days, List<StepIngredients> steps) {
	}

	private RoutineConflictRules() {
	}

	/**
	 * 루틴 하나의 경고. 결과는 WARNING → CAUTION → INFO 순.
	 *
	 * @param days   비면 매일
	 * @param others 같은 사용자의 다른 루틴. 반대 시간대이면서 요일이 겹치는 것만 "같은 날"로 본다
	 */
	public static List<RoutineWarning> check(RoutineSlot slot, Set<DayOfWeek> days, List<StepIngredients> steps,
			List<OtherRoutine> others) {
		List<RoutineWarning> warnings = new ArrayList<>();
		for (PairRule rule : PAIR_RULES) {
			List<Long> items = pairItems(rule, steps, steps);
			if (!items.isEmpty()) {
				warnings.add(new RoutineWarning(rule.level(), rule.code(), rule.message(), items));
			}
		}
		if (slot == RoutineSlot.MORNING) {
			List<Long> items = steps.stream()
					.filter(s -> !Collections.disjoint(s.groups(), SUN_SENSITIVE))
					.map(StepIngredients::shelfItemId).distinct().toList();
			if (!items.isEmpty()) {
				warnings.add(new RoutineWarning(WarningLevel.CAUTION, "MORNING_SUN_SENSITIVE",
						"아침 루틴에 레티노이드나 AHA·BHA가 있어요. 자외선에 예민해질 수 있으니 선크림을 꼭 발라 주세요.", items));
			}
		}
		if (slot == RoutineSlot.EVENING) {
			List<Long> items = steps.stream().filter(s -> s.stepType() == StepType.SUNSCREEN)
					.map(StepIngredients::shelfItemId).distinct().toList();
			if (!items.isEmpty()) {
				warnings.add(new RoutineWarning(WarningLevel.INFO, "EVENING_SUNSCREEN",
						"선크림은 아침 루틴에 쓰는 것을 권해요.", items));
			}
		}
		for (OtherRoutine other : others) {
			if (other.slot() == slot || !overlaps(days, other.days())) {
				continue;
			}
			for (PairRule rule : PAIR_RULES) {
				if (!rule.sameDay()) {
					continue;
				}
				List<Long> items = pairItems(rule, steps, other.steps());
				if (!items.isEmpty()) {
					warnings.add(new RoutineWarning(WarningLevel.INFO, "SAME_DAY",
							"같은 날 쓰는 '%s' 루틴과 함께 %s + %s 조합이 돼요. 자극이 느껴지면 요일을 나눠 보세요."
									.formatted(other.name(), rule.a().label(), rule.b().label()),
							items));
				}
			}
		}
		warnings.sort(Comparator.comparingInt(w -> w.level().ordinal()));
		return warnings;
	}

	/** 요일이 하루라도 겹치는가(비면 매일) */
	static boolean overlaps(Set<DayOfWeek> a, Set<DayOfWeek> b) {
		return a.isEmpty() || b.isEmpty() || !Collections.disjoint(a, b);
	}

	/**
	 * left 쪽에 그룹 a(또는 b), right 쪽에 다른 그룹이 있는 서로 다른 항목 쌍의 항목 ID.
	 * 한 제품 안에 두 그룹이 다 있는 것만으로는 경고하지 않는다(제품이 그렇게 만들어진 것).
	 */
	private static List<Long> pairItems(PairRule rule, List<StepIngredients> left, List<StepIngredients> right) {
		Set<Long> items = new LinkedHashSet<>();
		collect(rule.a(), rule.b(), left, right, items);
		collect(rule.b(), rule.a(), left, right, items);
		return List.copyOf(items);
	}

	private static void collect(IngredientGroup x, IngredientGroup y, List<StepIngredients> left,
			List<StepIngredients> right, Set<Long> items) {
		for (StepIngredients l : left) {
			if (!l.groups().contains(x)) {
				continue;
			}
			for (StepIngredients r : right) {
				if (r.groups().contains(y) && !r.shelfItemId().equals(l.shelfItemId())) {
					items.add(l.shelfItemId());
					items.add(r.shelfItemId());
				}
			}
		}
	}
}
