package com.mycosmetic.service.shelf;

import java.time.Clock;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import com.mycosmetic.dto.product.ProductSummaryResponse;
import com.mycosmetic.dto.shelf.ExpiryStatus;
import com.mycosmetic.dto.shelf.IngredientInsightsResponse;
import com.mycosmetic.dto.shelf.ShadeSeason;
import com.mycosmetic.dto.shelf.ShadeTone;
import com.mycosmetic.dto.shelf.ShelfItemResponse;
import com.mycosmetic.dto.shelf.ShelfStatsResponse;
import com.mycosmetic.dto.shelf.UpdateShelfRequest;
import com.mycosmetic.entity.Category;
import com.mycosmetic.entity.Ingredient;
import com.mycosmetic.entity.IngredientList;
import com.mycosmetic.entity.IngredientListItem;
import com.mycosmetic.entity.MakeupType;
import com.mycosmetic.entity.PersonalColor;
import com.mycosmetic.entity.ShelfItem;
import com.mycosmetic.entity.StepType;
import com.mycosmetic.entity.Verdict;
import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.repository.IngredientListItemRepository;
import com.mycosmetic.repository.IngredientListRepository;
import com.mycosmetic.repository.ShelfItemRepository;
import com.mycosmetic.repository.UserRepository;
import com.mycosmetic.service.ingredient.IngredientNameNormalizer;
import com.mycosmetic.service.product.ProductQueryService;
import com.mycosmetic.service.shade.ShadeClassifier;
import com.mycosmetic.service.skin.SkinProfileService;

/**
 * 내 화장대 (설계 3.4, 4.6). 🔒 항상 인증 사용자 ID로만 조회하고, 남의 항목 ID는 NOT_FOUND로 답한다.
 */
@Service
public class ShelfService {

	/** 성분으로 찾기 검색어 최대 길이(no-ai-features 설계 C) */
	static final int INGREDIENT_QUERY_MAX = 50;

	/** 사용기한·사용 일수의 "오늘" */
	static final ZoneId SEOUL = ZoneId.of("Asia/Seoul");

	private final ShelfItemRepository shelfItemRepository;
	private final IngredientListRepository listRepository;
	private final IngredientListItemRepository listItemRepository;
	private final UserRepository userRepository;
	private final ProductQueryService productQueryService;
	private final SkinProfileService skinProfileService;
	private final TransactionTemplate transactionTemplate;
	private final Clock clock;

	public ShelfService(ShelfItemRepository shelfItemRepository, IngredientListRepository listRepository,
			IngredientListItemRepository listItemRepository, UserRepository userRepository,
			ProductQueryService productQueryService, SkinProfileService skinProfileService,
			PlatformTransactionManager transactionManager, Clock clock) {
		this.shelfItemRepository = shelfItemRepository;
		this.listRepository = listRepository;
		this.listItemRepository = listItemRepository;
		this.userRepository = userRepository;
		this.productQueryService = productQueryService;
		this.skinProfileService = skinProfileService;
		this.transactionTemplate = new TransactionTemplate(transactionManager);
		this.clock = clock;
	}

	/**
	 * 성분 버전을 UNRATED로 담는다. 없는 버전은 NOT_FOUND, 이미 담았으면 SHELF_ITEM_DUPLICATE.
	 * 같은 버전을 동시에 담아 중복 불가 제약에 걸려도 SHELF_ITEM_DUPLICATE로 답한다.
	 */
	public ShelfItemResponse add(Long userId, Long ingredientListId) {
		try {
			return transactionTemplate.execute(status -> {
				IngredientList list = listRepository.findById(ingredientListId)
						.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
				if (shelfItemRepository.existsByUserIdAndIngredientListId(userId, ingredientListId)) {
					throw new BusinessException(ErrorCode.SHELF_ITEM_DUPLICATE);
				}
				ShelfItem item = shelfItemRepository.saveAndFlush(ShelfItem.create(userRepository.getReferenceById(userId),
						list.getProduct(), list, Verdict.UNRATED, null, null, null));
				return toResponse(item, userId);
			});
		} catch (DataIntegrityViolationException e) {
			throw new BusinessException(ErrorCode.SHELF_ITEM_DUPLICATE);
		}
	}

	/** 최근에 바꾼 순 */
	@Transactional(readOnly = true)
	public List<ShelfItemResponse> list(Long userId) {
		List<ShelfItem> items = shelfItemRepository.findWithProductByUserId(userId);
		return toResponses(items, skinProfileService.personalColor(userId));
	}

	/**
	 * 내 톤에 맞는 색조(no-ai-features 설계 E): MAKEUP 항목 중 호수 색이 있고 내 퍼스널컬러와 웜/쿨이 같은 것만, 최근에 바꾼 순.
	 * 퍼스널컬러를 고르지 않았으면 PERSONAL_COLOR_REQUIRED(400) — 빈 목록으로 답하면 "맞는 색조가 없다"와 구별되지 않는다
	 */
	@Transactional(readOnly = true)
	public List<ShelfItemResponse> toneMatch(Long userId) {
		PersonalColor personalColor = skinProfileService.personalColor(userId);
		if (personalColor == null) {
			throw new BusinessException(ErrorCode.PERSONAL_COLOR_REQUIRED);
		}
		List<ShelfItem> hits = shelfItemRepository.findWithProductByUserId(userId).stream()
				.filter(i -> i.getProduct().getCategory() == Category.MAKEUP)
				.filter(i -> Boolean.TRUE.equals(ShadeClassifier.matches(i.getShadeHex(), personalColor)))
				.toList();
		return toResponses(hits, personalColor);
	}

	private List<ShelfItemResponse> toResponses(List<ShelfItem> items, PersonalColor personalColor) {
		Map<Long, Long> reps = productQueryService
				.representativeListIds(items.stream().map(i -> i.getProduct().getId()).distinct().toList());
		LocalDate today = today();
		return items.stream().map(i -> toResponse(i, reps.get(i.getProduct().getId()), today, personalColor))
				.toList();
	}

	/**
	 * 성분으로 찾기(no-ai-features 설계 C): 내가 담은 버전의 성분 중 표준명 또는 원문을 정규화한 문자열에
	 * 검색어(정규화)가 들어 있는 항목만, 일치한 성분 이름과 함께. 공백은 양쪽 모두 빼고 비교한다("글라이콜릭 애씨드" = "글라이콜릭애씨드").
	 * 쿼리는 화장대 1번 + 성분 줄 1번 + 대표 버전 1번. 검색어가 비었거나 50자를 넘으면 INVALID_REQUEST
	 */
	@Transactional(readOnly = true)
	public List<ShelfItemResponse> searchByIngredient(Long userId, String q) {
		String key = ingredientSearchKey(q);
		List<ShelfItem> items = shelfItemRepository.findWithProductByUserId(userId);
		if (items.isEmpty()) {
			return List.of();
		}
		Map<Long, List<String>> matchedByList = matchedNamesByList(
				items.stream().map(i -> i.getIngredientList().getId()).distinct().toList(), key);
		List<ShelfItem> hits = items.stream()
				.filter(i -> matchedByList.containsKey(i.getIngredientList().getId())).toList();
		return toResponses(hits, skinProfileService.personalColor(userId)).stream()
				.map(r -> r.withMatchedIngredients(matchedByList.get(r.ingredientListId())))
				.toList();
	}

	/** 버전별로 검색어와 일치한 성분 이름(표준명, 매칭 안 됐으면 원문). 순서대로, 같은 이름은 한 번. 일치가 없는 버전은 빠진다 */
	private Map<Long, List<String>> matchedNamesByList(List<Long> listIds, String key) {
		List<IngredientListItem> lines = new ArrayList<>(listItemRepository.findWithIngredientByListIdIn(listIds));
		lines.sort(Comparator.comparing((IngredientListItem l) -> l.getList().getId())
				.thenComparingInt(IngredientListItem::getPosition));
		Map<Long, Set<String>> matched = new HashMap<>();
		for (IngredientListItem line : lines) {
			String standard = line.getIngredient() == null ? null : line.getIngredient().getStandardName();
			if (squash(line.getRawText()).contains(key) || (standard != null && squash(standard).contains(key))) {
				matched.computeIfAbsent(line.getList().getId(), k -> new LinkedHashSet<>())
						.add(standard != null ? standard : line.getRawText());
			}
		}
		Map<Long, List<String>> result = new HashMap<>();
		matched.forEach((listId, names) -> result.put(listId, List.copyOf(names)));
		return result;
	}

	static String ingredientSearchKey(String q) {
		String stripped = q == null ? "" : q.strip();
		if (stripped.isEmpty() || stripped.length() > INGREDIENT_QUERY_MAX) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST);
		}
		String key = squash(stripped);
		if (key.isEmpty()) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST);
		}
		return key;
	}

	private static String squash(String name) {
		return IngredientNameNormalizer.normalize(name).value().replace(" ", "");
	}

	/**
	 * null 필드는 그대로 두고, memo가 빈 문자열이면 지운다. 결과 종료일이 시작일보다 앞이거나
	 * 개봉일이 오늘(Asia/Seoul)보다 뒤면 INVALID_REQUEST. 사용기한(개월) 1~60은 요청 검증에서 막는다.
	 * 호수 이름·색은 빈 문자열이면 지우고, 색조(MAKEUP)가 아닌 항목에 값을 넣으면 INVALID_REQUEST.
	 * 제품 종류(stepType·makeupType)도 빈 문자열이면 지우고, 분류가 맞지 않거나 모르는 값이면 INVALID_ITEM_TYPE
	 */
	@Transactional
	public ShelfItemResponse update(Long userId, Long shelfItemId, UpdateShelfRequest request) {
		ShelfItem item = find(userId, shelfItemId);
		if (request.verdict() == Verdict.NEUTRAL) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST);
		}
		Verdict verdict = request.verdict() != null ? request.verdict() : item.getVerdict();
		String memo = item.getMemo();
		if (request.memo() != null) {
			memo = request.memo().isBlank() ? null : request.memo().strip();
		}
		LocalDate startedOn = request.startedOn() != null ? request.startedOn() : item.getStartedOn();
		LocalDate endedOn = request.endedOn() != null ? request.endedOn() : item.getEndedOn();
		if (startedOn != null && endedOn != null && endedOn.isBefore(startedOn)) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST);
		}
		LocalDate openedOn = request.openedOn() != null ? request.openedOn() : item.getOpenedOn();
		if (request.openedOn() != null && request.openedOn().isAfter(today())) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST);
		}
		Integer paoMonths = request.paoMonths() != null ? request.paoMonths() : item.getPaoMonths();
		String shadeName = item.getShadeName();
		if (request.shadeName() != null) {
			shadeName = request.shadeName().isBlank() ? null : request.shadeName().strip();
		}
		String shadeHex = item.getShadeHex();
		if (request.shadeHex() != null) {
			shadeHex = request.shadeHex().isEmpty() ? null : normalizeHex(request.shadeHex());
		}
		boolean settingShade = (request.shadeName() != null && !request.shadeName().isBlank())
				|| (request.shadeHex() != null && !request.shadeHex().isEmpty());
		if (settingShade && item.getProduct().getCategory() != Category.MAKEUP) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST);
		}
		Category category = item.getProduct().getCategory();
		StepType stepType = request.stepType() == null ? item.getStepType()
				: parseType(request.stepType(), StepType.class, category == Category.SKINCARE);
		MakeupType makeupType = request.makeupType() == null ? item.getMakeupType()
				: parseType(request.makeupType(), MakeupType.class, category == Category.MAKEUP);
		item.update(verdict, memo, startedOn, endedOn);
		item.changeOpening(openedOn, paoMonths);
		item.changeShade(shadeName, shadeHex);
		item.changeTypes(stepType, makeupType);
		shelfItemRepository.flush();
		return toResponse(item, userId);
	}

	/** 내 화장대 통계. 사용기한 임박·지남 목록은 다 쓴 항목을 빼고 쓰는 중인 항목만 담는다 */
	@Transactional(readOnly = true)
	public ShelfStatsResponse stats(Long userId) {
		List<ShelfItemResponse> items = list(userId);
		Map<Category, Long> byCategory = new EnumMap<>(Category.class);
		for (Category c : Category.values()) {
			byCategory.put(c, 0L);
		}
		Map<Verdict, Long> byVerdict = new EnumMap<>(Verdict.class);
		for (Verdict v : Verdict.values()) {
			if (v != Verdict.NEUTRAL) {
				byVerdict.put(v, 0L);
			}
		}
		long finished = 0;
		long usageSum = 0;
		long usageCount = 0;
		for (ShelfItemResponse item : items) {
			byCategory.merge(item.product().category(), 1L, Long::sum);
			byVerdict.merge(item.verdict(), 1L, Long::sum);
			if (item.endedOn() != null) {
				finished++;
				if (item.usageDays() != null) {
					usageSum += item.usageDays();
					usageCount++;
				}
			}
		}
		Double avg = usageCount == 0 ? null : Math.round(usageSum * 10.0 / usageCount) / 10.0;
		return new ShelfStatsResponse(items.size(), items.size() - finished, finished, byCategory, byVerdict,
				inUseWith(items, ExpiryStatus.EXPIRING), inUseWith(items, ExpiryStatus.EXPIRED), avg,
				ingredientInsights(items));
	}

	/** 맞음·안 맞음 항목의 성분 줄을 한 번에 읽어 성분별로 센다. 사전에 맞춘 성분은 ID로, 아니면 정규화한 이름으로 묶는다 */
	private IngredientInsightsResponse ingredientInsights(List<ShelfItemResponse> items) {
		List<ShelfItemResponse> rated = items.stream()
				.filter(i -> i.verdict() == Verdict.GOOD || i.verdict() == Verdict.BAD).toList();
		Set<Long> listIds = rated.stream().map(ShelfItemResponse::ingredientListId).collect(Collectors.toSet());
		Map<Long, List<IngredientInsights.Ingredient>> byList = new HashMap<>();
		if (!listIds.isEmpty()) {
			for (IngredientListItem line : listItemRepository.findWithIngredientByListIdIn(listIds)) {
				Ingredient ingredient = line.getIngredient();
				IngredientInsights.Ingredient key = ingredient != null
						? new IngredientInsights.Ingredient("id:" + ingredient.getId(), ingredient.getStandardName())
						: new IngredientInsights.Ingredient(
								"raw:" + IngredientNameNormalizer.normalize(line.getRawText()).value(), line.getRawText());
				byList.computeIfAbsent(line.getList().getId(), k -> new ArrayList<>()).add(key);
			}
		}
		List<IngredientInsights.Entry> entries = rated.stream()
				.map(i -> new IngredientInsights.Entry(i.verdict(), byList.getOrDefault(i.ingredientListId(), List.of())))
				.toList();
		return IngredientInsights.compute(entries);
	}

	private static List<ShelfItemResponse> inUseWith(List<ShelfItemResponse> items, ExpiryStatus status) {
		return items.stream().filter(i -> i.endedOn() == null && i.expiryStatus() == status)
				.sorted(Comparator.comparing(ShelfItemResponse::expiresOn).thenComparing(ShelfItemResponse::id))
				.toList();
	}

	@Transactional
	public void remove(Long userId, Long shelfItemId) {
		shelfItemRepository.delete(find(userId, shelfItemId));
	}

	private ShelfItem find(Long userId, Long shelfItemId) {
		return shelfItemRepository.findByIdAndUserId(shelfItemId, userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
	}

	/** 요청 검증을 거치지 않고 불린 경우에도 형식이 틀리면 INVALID_REQUEST */
	private static String normalizeHex(String hex) {
		try {
			return ShadeClassifier.normalize(hex);
		} catch (IllegalArgumentException e) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST);
		}
	}

	private ShelfItemResponse toResponse(ShelfItem item, Long userId) {
		Long productId = item.getProduct().getId();
		return toResponse(item, productQueryService.representativeListIds(List.of(productId)).get(productId),
				today(), skinProfileService.personalColor(userId));
	}

	/** 빈 문자열(공백만 포함)이면 null(지우기), 앞뒤 공백은 뺀다. 이 분류에서 쓸 수 없거나 모르는 이름이면 INVALID_ITEM_TYPE */
	private static <E extends Enum<E>> E parseType(String value, Class<E> type, boolean allowed) {
		if (value.isBlank()) {
			return null;
		}
		if (!allowed) {
			throw new BusinessException(ErrorCode.INVALID_ITEM_TYPE);
		}
		try {
			return Enum.valueOf(type, value.strip());
		} catch (IllegalArgumentException e) {
			throw new BusinessException(ErrorCode.INVALID_ITEM_TYPE);
		}
	}

	private LocalDate today() {
		return LocalDate.now(clock.withZone(SEOUL));
	}

	private static ShelfItemResponse toResponse(ShelfItem item, Long representativeListId, LocalDate today,
			PersonalColor personalColor) {
		Long listId = item.getIngredientList().getId();
		LocalDate expiresOn = ShelfUsage.expiresOn(item.getOpenedOn(), item.getPaoMonths());
		String hex = item.getShadeHex();
		ShadeClassifier.Shade shade = hex == null ? null : ShadeClassifier.classify(hex);
		ShadeTone tone = shade == null ? null : shade.tone();
		ShadeSeason season = shade == null ? null : shade.season();
		return new ShelfItemResponse(item.getId(), ProductSummaryResponse.of(item.getProduct(), representativeListId),
				listId, listId.equals(representativeListId), item.getVerdict(), item.getMemo(), item.getStartedOn(),
				item.getEndedOn(), item.getOpenedOn(), item.getPaoMonths(), expiresOn,
				ShelfUsage.status(expiresOn, today), ShelfUsage.usageDays(item.getStartedOn(), item.getEndedOn(), today),
				item.getShadeName(), hex, tone, season, ShadeClassifier.matches(hex, personalColor),
				// 제품 분류가 나중에 바뀌어도 맞지 않는 종류 값은 내보내지 않는다
				item.getProduct().getCategory() == Category.SKINCARE ? item.getStepType() : null,
				item.getProduct().getCategory() == Category.MAKEUP ? item.getMakeupType() : null, item.getUpdatedAt(),
				null);
	}
}
