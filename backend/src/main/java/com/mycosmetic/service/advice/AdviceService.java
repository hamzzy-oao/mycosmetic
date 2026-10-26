package com.mycosmetic.service.advice;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mycosmetic.dto.advice.AdviceReasonResponse;
import com.mycosmetic.dto.advice.AdviceRequest;
import com.mycosmetic.dto.advice.AdviceResponse;
import com.mycosmetic.dto.advice.RelatedShelfItem;
import com.mycosmetic.dto.product.ProductSummaryResponse;
import com.mycosmetic.entity.ExtractionJob;
import com.mycosmetic.entity.IngredientListItem;
import com.mycosmetic.entity.JobStatus;
import com.mycosmetic.entity.Product;
import com.mycosmetic.entity.Routine;
import com.mycosmetic.entity.ShelfItem;
import com.mycosmetic.entity.SkinProfile;
import com.mycosmetic.entity.SkinType;
import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.repository.ProductRepository;
import com.mycosmetic.repository.RoutineRepository;
import com.mycosmetic.repository.ShelfItemRepository;
import com.mycosmetic.repository.SkinProfileRepository;
import com.mycosmetic.service.ingredient.IngredientMatcher;
import com.mycosmetic.service.ingredient.MatchResult;
import com.mycosmetic.service.product.ProductQueryService;
import com.mycosmetic.service.routine.IngredientGroup;
import com.mycosmetic.service.routine.RoutineConflictRules.OtherRoutine;
import com.mycosmetic.service.routine.StepIngredients;

/**
 * "이거 사도 돼?" 규칙 판단(no-ai-features 설계 F). AI를 부르지 않고 내 기록과 성분 규칙({@link AdviceRules})만 쓴다.
 * 🔒 화장대·피부 상태·루틴은 항상 인증 사용자 ID로만 읽는다.
 * 아무것도 저장하지 않으며 하루 한도와 무관하다. 사진(AI)으로 판단하는 경로는 두지 않는다(2026-10-23 사용자 결정).
 */
@Service
@Transactional(readOnly = true)
public class AdviceService {

	private final ShelfItemRepository shelfItemRepository;
	private final ShelfEntryLoader shelfEntryLoader;
	private final ProductRepository productRepository;
	private final ProductQueryService productQueryService;
	private final SkinProfileRepository skinProfileRepository;
	private final RoutineRepository routineRepository;
	private final IngredientMatcher ingredientMatcher;

	public AdviceService(ShelfItemRepository shelfItemRepository, ShelfEntryLoader shelfEntryLoader,
			ProductRepository productRepository, ProductQueryService productQueryService,
			SkinProfileRepository skinProfileRepository, RoutineRepository routineRepository,
			IngredientMatcher ingredientMatcher) {
		this.shelfItemRepository = shelfItemRepository;
		this.shelfEntryLoader = shelfEntryLoader;
		this.productRepository = productRepository;
		this.productQueryService = productQueryService;
		this.skinProfileRepository = skinProfileRepository;
		this.routineRepository = routineRepository;
		this.ingredientMatcher = ingredientMatcher;
	}

	/**
	 * 판단 대상.
	 *
	 * @param matched 성분 사전에 매칭된 줄 수
	 * @param product 제품으로 골랐을 때 그 제품, 아니면 null
	 * @param listId  제품의 대표 버전 ID, 아니면 null
	 */
	private record Resolved(List<AdviceIngredient> ingredients, int matched, Product product, Long listId) {
	}

	/**
	 * productId·ingredientsText 중 정확히 하나로 판단한다(아니면 INVALID_REQUEST).
	 * 없는 제품·성분 버전이 없는 제품은 NOT_FOUND
	 */
	public AdviceResponse advise(Long userId, AdviceRequest request) {
		long given = Stream.of(request.productId(), request.ingredientsText())
				.filter(Objects::nonNull).count();
		if (given != 1) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST);
		}
		List<ShelfItem> shelf = shelfItemRepository.findWithProductByUserId(userId);
		Resolved target;
		ShelfEntryLoader.Loaded loaded;
		if (request.productId() != null) {
			Product product = productRepository.findById(request.productId())
					.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
			Long listId = productQueryService.representativeListIds(List.of(product.getId())).get(product.getId());
			if (listId == null) {
				throw new BusinessException(ErrorCode.NOT_FOUND);
			}
			loaded = shelfEntryLoader.load(shelf, listId);
			List<IngredientListItem> own = loaded.lines().getOrDefault(listId, List.of());
			int matched = (int) own.stream().filter(l -> l.getIngredient() != null).count();
			target = new Resolved(ShelfEntryLoader.toIngredients(own), matched, product, listId);
		} else {
			List<MatchResult> matches = ingredientMatcher.match(IngredientsTextParser.parse(request.ingredientsText()));
			loaded = shelfEntryLoader.load(shelf, null);
			List<AdviceIngredient> ingredients = matches.stream()
					.map(m -> AdviceIngredient.of(m.ingredientId(), m.standardName(), m.rawText())).toList();
			int matched = (int) matches.stream().filter(m -> m.ingredientId() != null).count();
			target = new Resolved(ingredients, matched, null, null);
		}

		Map<Long, List<AdviceIngredient>> ingredientsByList = loaded.ingredientsByList();
		List<ShelfEntry> entries = loaded.entries();
		Set<SkinType> skinTypes = skinProfileRepository.findById(userId).map(SkinProfile::getSkinTypes)
				.orElse(Set.of());
		List<OtherRoutine> routines = routines(userId, ingredientsByList);

		Long productId = target.product() == null ? null : target.product().getId();
		AdviceResult result = AdviceRules.evaluate(new AdviceRules.Target(productId, target.ingredients()), entries,
				skinTypes, routines);
		return toResponse(result, target, shelf);
	}

	/** 내 루틴들. 단계의 성분 그룹은 화장대 항목(내가 담은 버전)의 성분으로 정한다 */
	private List<OtherRoutine> routines(Long userId, Map<Long, List<AdviceIngredient>> ingredientsByList) {
		List<OtherRoutine> routines = new ArrayList<>();
		for (Routine routine : routineRepository.findWithStepsByUserId(userId)) {
			List<StepIngredients> steps = routine.getSteps().stream().map(step -> {
				ShelfItem item = step.getShelfItem();
				List<String> names = ingredientsByList.getOrDefault(item.getIngredientList().getId(), List.of())
						.stream().flatMap(i -> i.names().stream()).toList();
				return new StepIngredients(item.getId(), step.getStepType(), IngredientGroup.detect(names));
			}).toList();
			routines.add(new OtherRoutine(routine.getName(), routine.getSlot(), routine.getDays(), steps));
		}
		return routines;
	}

	private static AdviceResponse toResponse(AdviceResult result, Resolved target, List<ShelfItem> shelf) {
		Set<Long> relatedIds = new LinkedHashSet<>();
		result.reasons().forEach(r -> relatedIds.addAll(r.relatedShelfItemIds()));
		Map<Long, ShelfItem> byId = shelf.stream().collect(Collectors.toMap(ShelfItem::getId, Function.identity()));
		List<RelatedShelfItem> related = relatedIds.stream().map(byId::get).filter(Objects::nonNull)
				.map(RelatedShelfItem::of).toList();
		List<String> names = AdviceRules.distinct(target.ingredients()).stream().map(AdviceIngredient::name).toList();
		ProductSummaryResponse product = target.product() == null ? null
				: ProductSummaryResponse.of(target.product(), target.listId());
		boolean onShelf = target.product() != null
				&& shelf.stream().anyMatch(i -> i.getProduct().getId().equals(target.product().getId()));
		int total = target.ingredients().size();
		return new AdviceResponse(result.verdict(), result.reasons().stream().map(AdviceReasonResponse::of).toList(),
				target.matched(), total - target.matched(), names, product, onShelf, related);
	}
}
