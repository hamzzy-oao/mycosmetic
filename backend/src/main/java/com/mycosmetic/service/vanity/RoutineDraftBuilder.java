package com.mycosmetic.service.vanity;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mycosmetic.entity.RoutineSlot;
import com.mycosmetic.entity.SkinConcern;
import com.mycosmetic.entity.StepType;
import com.mycosmetic.entity.Verdict;
import com.mycosmetic.service.routine.RoutineConflictRules;
import com.mycosmetic.service.routine.RoutineConflictRules.OtherRoutine;
import com.mycosmetic.service.routine.RoutineWarning;
import com.mycosmetic.service.routine.StepIngredients;
import com.mycosmetic.service.vanity.RoutineDraft.Alternate;
import com.mycosmetic.service.vanity.RoutineDraft.DraftStep;
import com.mycosmetic.service.vanity.RoutineDraft.Gap;

/**
 * 보유 제품으로 아침·저녁 루틴 초안을 만든다(퍼스널 탭 설계 3절 A). 순수 함수라 DB 없이 단위 테스트한다.
 * <ul>
 * <li>단계 종류(StepType) 순서로 놓는다. 마스크·기타는 매일 루틴에 넣지 않는다</li>
 * <li>선크림은 아침에만, 레티노이드·AHA·BHA가 든 제품은 저녁에만</li>
 * <li>슬롯마다 같은 단계는 하나: 잘맞아요 → 최근 시작 → id 순. 어느 슬롯에도 들지 못한 같은 단계 제품은 교대 후보</li>
 * <li>세안·보습(로션 또는 크림)·아침 선크림이 비면 빈 자리로 알린다. 토너·세럼 등은 비어도 알리지 않는다</li>
 * <li>초안에 기존 루틴 충돌 규칙을 그대로 적용한다</li>
 * </ul>
 */
public final class RoutineDraftBuilder {

	static final Set<StepType> NOT_DAILY = Set.of(StepType.MASK, StepType.ETC);

	private static final Comparator<DraftItem> PREFERRED = Comparator
			.comparing((DraftItem i) -> i.verdict() != Verdict.GOOD)
			.thenComparing(DraftItem::startedOn, Comparator.nullsLast(Comparator.<LocalDate>reverseOrder()))
			.thenComparing(DraftItem::shelfItemId);

	private RoutineDraftBuilder() {
	}

	public static RoutineDraft build(List<DraftItem> items, Set<SkinConcern> concerns) {
		List<DraftItem> daily = items.stream().filter(i -> !NOT_DAILY.contains(i.stepType())).toList();
		List<DraftItem> morning = pick(daily, RoutineSlot.MORNING);
		List<DraftItem> evening = pick(daily, RoutineSlot.EVENING);

		Set<Long> used = new HashSet<>();
		morning.forEach(i -> used.add(i.shelfItemId()));
		evening.forEach(i -> used.add(i.shelfItemId()));
		Map<StepType, List<Long>> leftover = new EnumMap<>(StepType.class);
		daily.stream().filter(i -> !used.contains(i.shelfItemId())).sorted(PREFERRED)
				.forEach(i -> leftover.computeIfAbsent(i.stepType(), k -> new ArrayList<>()).add(i.shelfItemId()));
		List<Alternate> alternates = leftover.entrySet().stream()
				.map(e -> new Alternate(e.getKey(), e.getValue())).toList();

		List<StepIngredients> morningSteps = steps(morning);
		List<StepIngredients> eveningSteps = steps(evening);
		List<RoutineWarning> warnings = new ArrayList<>(
				RoutineConflictRules.check(RoutineSlot.MORNING, Set.of(), morningSteps, List.of()));
		// "같은 날" 정보는 한쪽에서만 받는다(양쪽에서 부르면 같은 문장이 두 번 나온다)
		warnings.addAll(RoutineConflictRules.check(RoutineSlot.EVENING, Set.of(), eveningSteps,
				List.of(new OtherRoutine("아침 초안", RoutineSlot.MORNING, Set.of(), morningSteps))));
		warnings.sort(Comparator.comparingInt(w -> w.level().ordinal()));

		return new RoutineDraft(toSteps(morning), toSteps(evening), warnings, gaps(morning, evening, concerns),
				alternates);
	}

	private static List<DraftItem> pick(List<DraftItem> daily, RoutineSlot slot) {
		Map<StepType, DraftItem> best = new EnumMap<>(StepType.class);
		daily.stream().filter(i -> allowed(i, slot)).sorted(PREFERRED)
				.forEach(i -> best.putIfAbsent(i.stepType(), i));
		return List.copyOf(best.values()); // EnumMap이라 단계 종류 순서다
	}

	private static boolean allowed(DraftItem item, RoutineSlot slot) {
		boolean sunSensitive = !Collections.disjoint(item.groups(), RoutineConflictRules.SUN_SENSITIVE);
		if (slot == RoutineSlot.MORNING) {
			return !sunSensitive;
		}
		return item.stepType() != StepType.SUNSCREEN;
	}

	private static List<Gap> gaps(List<DraftItem> morning, List<DraftItem> evening, Set<SkinConcern> concerns) {
		List<DraftItem> all = new ArrayList<>(morning);
		all.addAll(evening);
		List<Gap> gaps = new ArrayList<>();
		if (all.stream().noneMatch(i -> i.stepType() == StepType.CLEANSER)) {
			gaps.add(new Gap("CLEANSER", "ESSENTIAL", "세안제가 내 화장대에 없어요. 가지고 있다면 등록해 주세요.", List.of(),
					"tip-cleanser"));
		}
		if (all.stream().noneMatch(i -> i.stepType() == StepType.LOTION || i.stepType() == StepType.CREAM)) {
			List<String> hints = concerns.isEmpty() ? ConcernIngredientHints.DEFAULT_MOISTURIZER
					: ConcernIngredientHints.forConcerns(concerns);
			gaps.add(new Gap("MOISTURIZER", "ESSENTIAL", "보습 제품(로션·크림)이 없어요. 가지고 있다면 등록해 주세요.", hints,
					"tip-moisturizer"));
		}
		if (morning.stream().noneMatch(i -> i.stepType() == StepType.SUNSCREEN)) {
			gaps.add(new Gap("SUNSCREEN", "ESSENTIAL", "아침 루틴에 선크림이 없어요. 가지고 있다면 등록해 주세요.",
					List.of("자외선 차단 제품"), "tip-sunscreen"));
		}
		return gaps;
	}

	private static List<StepIngredients> steps(List<DraftItem> items) {
		return items.stream().map(i -> new StepIngredients(i.shelfItemId(), i.stepType(), i.groups())).toList();
	}

	private static List<DraftStep> toSteps(List<DraftItem> items) {
		return items.stream().map(i -> new DraftStep(i.shelfItemId(), i.stepType(), i.guessed())).toList();
	}
}
