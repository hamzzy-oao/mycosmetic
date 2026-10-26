package com.mycosmetic.service.routine;

import java.time.Clock;
import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mycosmetic.dto.routine.RoutineCheckRequest;
import com.mycosmetic.dto.routine.RoutineCheckResponse;
import com.mycosmetic.dto.routine.RoutineRequest;
import com.mycosmetic.dto.routine.RoutineResponse;
import com.mycosmetic.dto.routine.RoutineUsageResponse;
import com.mycosmetic.dto.routine.StepRequest;
import com.mycosmetic.dto.routine.TodayRoutinesResponse;
import com.mycosmetic.dto.routine.WarningResponse;
import com.mycosmetic.dto.vanity.VanityAnalysisResponse;
import com.mycosmetic.entity.IngredientListItem;
import com.mycosmetic.entity.Routine;
import com.mycosmetic.entity.RoutineCheck;
import com.mycosmetic.entity.RoutineSlot;
import com.mycosmetic.entity.RoutineStep;
import com.mycosmetic.entity.ShelfItem;
import com.mycosmetic.entity.StepType;
import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.repository.IngredientListItemRepository;
import com.mycosmetic.repository.RoutineCheckRepository;
import com.mycosmetic.repository.RoutineRepository;
import com.mycosmetic.repository.ShelfItemRepository;
import com.mycosmetic.repository.UserRepository;
import com.mycosmetic.service.routine.RoutineConflictRules.OtherRoutine;
import com.mycosmetic.service.vanity.RoutineDraft.DraftStep;
import com.mycosmetic.service.vanity.VanityAnalysisService;

/**
 * 루틴 플래너(no-ai-features 설계 A). 🔒 항상 인증 사용자 ID로만 조회하고, 남의 루틴·화장대 항목 ID는 NOT_FOUND로 답한다.
 * 충돌 판정은 각 항목의 "내가 담은 버전"(shelf_items.ingredient_list_id) 성분으로 한다.
 */
@Service
public class RoutineService {

	static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");
	/** 사용 기록을 세는 기간(오늘 포함)과 "꾸준히 쓴 제품"의 기준(기간의 절반) */
	static final int USAGE_DAYS = 28;
	static final int STEADY_DAYS = 14;

	private final RoutineRepository routineRepository;
	private final ShelfItemRepository shelfItemRepository;
	private final IngredientListItemRepository listItemRepository;
	private final UserRepository userRepository;
	private final RoutineCheckRepository checkRepository;
	private final VanityAnalysisService vanityAnalysisService;
	private final Clock clock;

	public RoutineService(RoutineRepository routineRepository, ShelfItemRepository shelfItemRepository,
			IngredientListItemRepository listItemRepository, UserRepository userRepository,
			RoutineCheckRepository checkRepository, VanityAnalysisService vanityAnalysisService, Clock clock) {
		this.routineRepository = routineRepository;
		this.shelfItemRepository = shelfItemRepository;
		this.listItemRepository = listItemRepository;
		this.userRepository = userRepository;
		this.checkRepository = checkRepository;
		this.vanityAnalysisService = vanityAnalysisService;
		this.clock = clock;
	}

	/** 만든 순 */
	@Transactional(readOnly = true)
	public List<RoutineResponse> list(Long userId) {
		return routineRepository.findWithStepsByUserId(userId).stream().map(RoutineResponse::of).toList();
	}

	@Transactional(readOnly = true)
	public RoutineResponse get(Long userId, Long routineId) {
		return RoutineResponse.of(find(userId, routineId));
	}

	@Transactional
	public RoutineResponse create(Long userId, RoutineRequest request) {
		List<ShelfItem> items = resolveItems(userId, request.steps());
		Routine routine = Routine.create(userRepository.getReferenceById(userId), request.name().strip(),
				request.slot(), request.days());
		routine.replaceSteps(items, stepTypes(request));
		return RoutineResponse.of(routineRepository.saveAndFlush(routine));
	}

	/** 이름·시간대·요일·단계를 통째로 바꾼다 */
	@Transactional
	public RoutineResponse update(Long userId, Long routineId, RoutineRequest request) {
		Routine routine = find(userId, routineId);
		List<ShelfItem> items = resolveItems(userId, request.steps());
		routine.change(request.name().strip(), request.slot(), request.days());
		routine.replaceSteps(items, stepTypes(request));
		routineRepository.flush();
		return RoutineResponse.of(routine);
	}

	@Transactional
	public void delete(Long userId, Long routineId) {
		routineRepository.delete(find(userId, routineId));
	}

	/**
	 * 화장대 진단의 "루틴으로 저장". 지금 화장대로 초안을 다시 계산해(화면이 보낸 값을 믿지 않는다) 매일 하는 아침·저녁 루틴으로
	 * 저장한다. 전에 진단에서 저장한 루틴은 지우고 바꾼다(여러 번 눌러도 루틴이 쌓이지 않는다). 직접 만든 루틴은 건드리지 않는다.
	 * 초안이 빈 시간대는 만들지 않고, 둘 다 비면 INVALID_REQUEST.
	 *
	 * @return 저장 후 내 루틴 전부(만든 순)
	 */
	@Transactional
	public List<RoutineResponse> saveFromDiagnosis(Long userId) {
		VanityAnalysisResponse.Draft draft = vanityAnalysisService.analyze(userId).routineDraft();
		if (draft.morning().isEmpty() && draft.evening().isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST);
		}
		routineRepository.findWithStepsByUserId(userId).stream().filter(Routine::isFromDiagnosis)
				.forEach(routineRepository::delete);
		routineRepository.flush();
		saveDraftSlot(userId, RoutineSlot.MORNING, "아침 루틴", draft.morning());
		saveDraftSlot(userId, RoutineSlot.EVENING, "저녁 루틴", draft.evening());
		routineRepository.flush();
		return list(userId);
	}

	private void saveDraftSlot(Long userId, RoutineSlot slot, String name, List<DraftStep> steps) {
		if (steps.isEmpty()) {
			return;
		}
		List<ShelfItem> items = resolveItems(userId,
				steps.stream().map(s -> new StepRequest(s.shelfItemId(), s.stepType())).toList());
		Routine routine = Routine.fromDiagnosis(userRepository.getReferenceById(userId), name, slot);
		routine.replaceSteps(items, steps.stream().map(DraftStep::stepType).toList());
		routineRepository.save(routine);
	}

	/**
	 * 오늘(Asia/Seoul) 이 시간대에 쓴 제품 체크·해제. 이미 체크한 것을 다시 체크하거나 없는 체크를 해제해도 그대로다.
	 * 남의 것·없는 화장대 항목이 하나라도 있으면 NOT_FOUND이고 아무것도 바뀌지 않는다.
	 */
	@Transactional
	public TodayRoutinesResponse checkToday(Long userId, RoutineCheckRequest request) {
		LocalDate today = LocalDate.now(clock.withZone(SEOUL));
		Set<Long> ids = new HashSet<>(request.shelfItemIds());
		List<ShelfItem> items = shelfItemRepository.findWithProductByUserIdAndIdIn(userId, ids);
		if (items.size() != ids.size()) {
			throw new BusinessException(ErrorCode.NOT_FOUND);
		}
		if (Boolean.TRUE.equals(request.checked())) {
			Set<Long> done = checkRepository.findByUserIdAndUsedOn(userId, today).stream()
					.filter(c -> c.getSlot() == request.slot()).map(c -> c.getShelfItem().getId())
					.collect(Collectors.toSet());
			items.stream().filter(i -> !done.contains(i.getId())).forEach(i -> checkRepository
					.save(RoutineCheck.of(userRepository.getReferenceById(userId), i, today, request.slot())));
			checkRepository.flush();
		} else {
			checkRepository.deleteChecks(userId, today, request.slot(), ids);
		}
		return today(userId);
	}

	/** 최근 USAGE_DAYS일(오늘 포함) 동안 제품별로 체크한 날짜 수. 많이 쓴 순, 같으면 항목 ID 순 */
	@Transactional(readOnly = true)
	public RoutineUsageResponse usage(Long userId) {
		LocalDate today = LocalDate.now(clock.withZone(SEOUL));
		Map<Long, ShelfItem> items = new LinkedHashMap<>();
		Map<Long, Set<LocalDate>> dates = new HashMap<>();
		for (RoutineCheck c : checkRepository.findWithItemByUserIdAndUsedOnBetween(userId,
				today.minusDays(USAGE_DAYS - 1L), today)) {
			ShelfItem item = c.getShelfItem();
			items.putIfAbsent(item.getId(), item);
			dates.computeIfAbsent(item.getId(), k -> new HashSet<>()).add(c.getUsedOn());
		}
		List<RoutineUsageResponse.Item> result = items.values().stream().map(i -> {
			int used = dates.get(i.getId()).size();
			var product = i.getProduct();
			return new RoutineUsageResponse.Item(i.getId(), product.getBrand(), product.getName(),
					product.getCategory(), i.getVerdict(), used, used >= STEADY_DAYS);
		}).sorted(Comparator.comparingInt(RoutineUsageResponse.Item::daysUsed).reversed()
				.thenComparing(RoutineUsageResponse.Item::shelfItemId)).toList();
		return new RoutineUsageResponse(USAGE_DAYS, STEADY_DAYS, result);
	}

	/** 저장된 루틴의 경고와 권장 순서 */
	@Transactional(readOnly = true)
	public RoutineCheckResponse check(Long userId, Long routineId) {
		Routine routine = find(userId, routineId);
		List<Routine> all = routineRepository.findWithStepsByUserId(userId);
		Map<Long, Set<IngredientGroup>> groups = groupsByList(all.stream()
				.flatMap(r -> r.getSteps().stream()).map(s -> s.getShelfItem().getIngredientList().getId()).toList());
		List<StepIngredients> steps = routine.getSteps().stream().map(s -> toStep(s, groups)).toList();
		return evaluate(routine.getSlot(), routine.getDays(), steps, others(all, routineId, groups));
	}

	/**
	 * 저장하기 전 편집 중인 루틴의 경고와 권장 순서(화면의 실시간 경고용).
	 *
	 * @param routineId 고치는 중인 루틴. 있으면 "같은 날" 비교에서 뺀다. 남의 루틴이면 NOT_FOUND
	 */
	@Transactional(readOnly = true)
	public RoutineCheckResponse checkDraft(Long userId, Long routineId, RoutineRequest request) {
		if (routineId != null) {
			find(userId, routineId);
		}
		List<ShelfItem> items = resolveItems(userId, request.steps());
		List<Routine> all = routineRepository.findWithStepsByUserId(userId);
		List<Long> listIds = new ArrayList<>(items.stream().map(i -> i.getIngredientList().getId()).toList());
		all.forEach(r -> r.getSteps().forEach(s -> listIds.add(s.getShelfItem().getIngredientList().getId())));
		Map<Long, Set<IngredientGroup>> groups = groupsByList(listIds);
		List<StepType> types = stepTypes(request);
		List<StepIngredients> steps = new ArrayList<>();
		for (int i = 0; i < items.size(); i++) {
			ShelfItem item = items.get(i);
			steps.add(new StepIngredients(item.getId(), types.get(i),
					groups.getOrDefault(item.getIngredientList().getId(), Set.of())));
		}
		Set<DayOfWeek> days = request.days() == null ? Set.of() : request.days();
		return evaluate(request.slot(), days, steps, others(all, routineId, groups));
	}

	/** 오늘(Asia/Seoul) 요일에 하는 아침·저녁 루틴, 오늘 체크, 오늘 루틴의 성분 충돌 경고 */
	@Transactional(readOnly = true)
	public TodayRoutinesResponse today(Long userId) {
		LocalDate today = LocalDate.now(clock.withZone(SEOUL));
		DayOfWeek day = today.getDayOfWeek();
		List<Routine> todays = routineRepository.findWithStepsByUserId(userId).stream()
				.filter(r -> r.runsOn(day)).toList();
		List<RoutineCheck> checks = checkRepository.findByUserIdAndUsedOn(userId, today);
		return new TodayRoutinesResponse(today, day, bySlot(todays, RoutineSlot.MORNING),
				bySlot(todays, RoutineSlot.EVENING), checkedIn(checks, RoutineSlot.MORNING),
				checkedIn(checks, RoutineSlot.EVENING), todayWarnings(todays));
	}

	private static List<Long> checkedIn(List<RoutineCheck> checks, RoutineSlot slot) {
		return checks.stream().filter(c -> c.getSlot() == slot).map(c -> c.getShelfItem().getId()).sorted().toList();
	}

	/**
	 * 오늘 아침 루틴들은 한 번에 바르는 것으로, 저녁 루틴들도 그렇게 보고 충돌 규칙을 적용한다. "같은 날" 정보는 저녁 쪽에서만
	 * 받는다(양쪽에서 부르면 같은 문장이 두 번 나온다 — 화장대 진단 초안과 같은 방식).
	 */
	private List<WarningResponse> todayWarnings(List<Routine> todays) {
		Map<Long, Set<IngredientGroup>> groups = groupsByList(todays.stream().flatMap(r -> r.getSteps().stream())
				.map(s -> s.getShelfItem().getIngredientList().getId()).toList());
		List<StepIngredients> morning = slotSteps(todays, RoutineSlot.MORNING, groups);
		List<StepIngredients> evening = slotSteps(todays, RoutineSlot.EVENING, groups);
		List<RoutineWarning> warnings = new ArrayList<>(
				RoutineConflictRules.check(RoutineSlot.MORNING, Set.of(), morning, List.of()));
		warnings.addAll(RoutineConflictRules.check(RoutineSlot.EVENING, Set.of(), evening,
				List.of(new OtherRoutine("오늘 아침", RoutineSlot.MORNING, Set.of(), morning))));
		return warnings.stream().sorted(Comparator.comparingInt(w -> w.level().ordinal()))
				.map(WarningResponse::of).toList();
	}

	/** 이 시간대 루틴들의 단계. 같은 제품이 두 루틴에 있으면 한 번만 */
	private static List<StepIngredients> slotSteps(List<Routine> todays, RoutineSlot slot,
			Map<Long, Set<IngredientGroup>> groups) {
		Map<Long, StepIngredients> steps = new LinkedHashMap<>();
		todays.stream().filter(r -> r.getSlot() == slot).flatMap(r -> r.getSteps().stream())
				.forEach(s -> steps.putIfAbsent(s.getShelfItem().getId(), toStep(s, groups)));
		return List.copyOf(steps.values());
	}

	private static List<RoutineResponse> bySlot(List<Routine> routines, RoutineSlot slot) {
		return routines.stream().filter(r -> r.getSlot() == slot).map(RoutineResponse::of).toList();
	}

	private static RoutineCheckResponse evaluate(RoutineSlot slot, Set<DayOfWeek> days, List<StepIngredients> steps,
			List<OtherRoutine> others) {
		List<WarningResponse> warnings = RoutineConflictRules.check(slot, days, steps, others).stream()
				.map(WarningResponse::of).toList();
		return new RoutineCheckResponse(warnings, RoutineOrder.suggest(steps));
	}

	private static List<OtherRoutine> others(List<Routine> all, Long excludeId,
			Map<Long, Set<IngredientGroup>> groups) {
		return all.stream().filter(r -> !r.getId().equals(excludeId))
				.map(r -> new OtherRoutine(r.getName(), r.getSlot(), r.getDays(),
						r.getSteps().stream().map(s -> toStep(s, groups)).toList()))
				.toList();
	}

	private static StepIngredients toStep(RoutineStep step, Map<Long, Set<IngredientGroup>> groups) {
		ShelfItem item = step.getShelfItem();
		return new StepIngredients(item.getId(), step.getStepType(),
				groups.getOrDefault(item.getIngredientList().getId(), Set.of()));
	}

	/** 성분 버전별 성분 그룹. 표준명과 원문을 모두 본다 */
	private Map<Long, Set<IngredientGroup>> groupsByList(Collection<Long> listIds) {
		Set<Long> ids = new HashSet<>(listIds);
		if (ids.isEmpty()) {
			return Map.of();
		}
		Map<Long, List<String>> names = new HashMap<>();
		for (IngredientListItem line : listItemRepository.findWithIngredientByListIdIn(ids)) {
			List<String> n = names.computeIfAbsent(line.getList().getId(), k -> new ArrayList<>());
			n.add(line.getRawText());
			if (line.getIngredient() != null) {
				n.add(line.getIngredient().getStandardName());
			}
		}
		return names.entrySet().stream()
				.collect(Collectors.toMap(Map.Entry::getKey, e -> IngredientGroup.detect(e.getValue())));
	}

	/** 요청 순서대로 내 화장대 항목. 중복이면 INVALID_REQUEST, 남의 것·없는 것이 있으면 NOT_FOUND */
	private List<ShelfItem> resolveItems(Long userId, List<StepRequest> steps) {
		List<Long> ids = steps.stream().map(StepRequest::shelfItemId).toList();
		if (new HashSet<>(ids).size() != ids.size()) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST);
		}
		if (ids.isEmpty()) {
			return List.of();
		}
		Map<Long, ShelfItem> mine = shelfItemRepository.findWithProductByUserIdAndIdIn(userId, ids).stream()
				.collect(Collectors.toMap(ShelfItem::getId, Function.identity()));
		if (mine.size() != ids.size()) {
			throw new BusinessException(ErrorCode.NOT_FOUND);
		}
		return ids.stream().map(mine::get).toList();
	}

	private static List<StepType> stepTypes(RoutineRequest request) {
		return request.steps().stream().map(StepRequest::stepType).toList();
	}

	private Routine find(Long userId, Long routineId) {
		return routineRepository.findByIdAndUserId(routineId, userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
	}
}
