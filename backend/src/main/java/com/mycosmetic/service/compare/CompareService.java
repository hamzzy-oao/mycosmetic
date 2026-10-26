package com.mycosmetic.service.compare;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mycosmetic.dto.compare.CompareResponse;
import com.mycosmetic.dto.product.FinalItem;
import com.mycosmetic.dto.product.ProductSummaryResponse;
import com.mycosmetic.entity.IngredientListItem;
import com.mycosmetic.entity.Product;
import com.mycosmetic.entity.ShelfItem;
import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.repository.IngredientListItemRepository;
import com.mycosmetic.repository.ProductRepository;
import com.mycosmetic.repository.ShelfItemRepository;
import com.mycosmetic.service.product.ListFingerprint;
import com.mycosmetic.service.product.ProductQueryService;
import com.mycosmetic.service.routine.IngredientGroup;

/**
 * 제품 비교(no-ai-features 설계 B). 각 쪽은 내 화장대 항목(내가 담은 버전) 또는 제품(대표 버전)이다.
 * 성분은 fingerprint와 같은 토큰({@link ListFingerprint#token})으로 비교한다.
 * 🔒 화장대 항목은 인증 사용자 ID로만 찾고, 남의 항목 ID는 NOT_FOUND로 답한다.
 */
@Service
@Transactional(readOnly = true)
public class CompareService {

	private final ShelfItemRepository shelfItemRepository;
	private final ProductRepository productRepository;
	private final IngredientListItemRepository listItemRepository;
	private final ProductQueryService productQueryService;

	public CompareService(ShelfItemRepository shelfItemRepository, ProductRepository productRepository,
			IngredientListItemRepository listItemRepository, ProductQueryService productQueryService) {
		this.shelfItemRepository = shelfItemRepository;
		this.productRepository = productRepository;
		this.listItemRepository = listItemRepository;
		this.productQueryService = productQueryService;
	}

	/**
	 * 각 쪽은 화장대 항목 ID와 제품 ID 중 정확히 하나로 정한다(둘 다 없거나 둘 다 있으면 INVALID_REQUEST).
	 * 남의·없는 화장대 항목, 없는 제품, 성분 버전이 없는 제품은 NOT_FOUND
	 */
	public CompareResponse compare(Long userId, Long a, Long aProduct, Long b, Long bProduct) {
		Picked left = pick(userId, a, aProduct);
		Picked right = pick(userId, b, bProduct);
		Map<Long, Long> reps = productQueryService
				.representativeListIds(List.of(left.product().getId(), right.product().getId()));
		Map<Long, List<IngredientListItem>> lines = linesByList(left.listId(), right.listId());
		Map<String, String> tokensA = tokens(lines.getOrDefault(left.listId(), List.of()));
		Map<String, String> tokensB = tokens(lines.getOrDefault(right.listId(), List.of()));

		List<String> common = new ArrayList<>();
		List<String> onlyA = new ArrayList<>();
		tokensA.forEach((token, name) -> (tokensB.containsKey(token) ? common : onlyA).add(name));
		List<String> onlyB = tokensB.entrySet().stream()
				.filter(e -> !tokensA.containsKey(e.getKey())).map(Map.Entry::getValue).toList();
		return new CompareResponse(side(left, reps), side(right, reps), common, onlyA, onlyB,
				groups(lines.getOrDefault(left.listId(), List.of())),
				groups(lines.getOrDefault(right.listId(), List.of())));
	}

	private record Picked(Product product, Long listId) {
	}

	private Picked pick(Long userId, Long shelfItemId, Long productId) {
		if ((shelfItemId == null) == (productId == null)) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST);
		}
		if (shelfItemId != null) {
			ShelfItem item = shelfItemRepository.findByIdAndUserId(shelfItemId, userId)
					.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
			return new Picked(item.getProduct(), item.getIngredientList().getId());
		}
		Product product = productRepository.findById(productId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
		Long rep = productQueryService.representativeListIds(List.of(productId)).get(productId);
		if (rep == null) {
			throw new BusinessException(ErrorCode.NOT_FOUND);
		}
		return new Picked(product, rep);
	}

	private static CompareResponse.Side side(Picked picked, Map<Long, Long> reps) {
		return new CompareResponse.Side(
				ProductSummaryResponse.of(picked.product(), reps.get(picked.product().getId())), picked.listId());
	}

	/** 두 버전의 성분 줄을 한 번에 읽어 버전별·순서대로 나눈다 */
	private Map<Long, List<IngredientListItem>> linesByList(Long listA, Long listB) {
		return listItemRepository.findWithIngredientByListIdIn(new HashSet<>(List.of(listA, listB))).stream()
				.sorted(Comparator.comparingInt(IngredientListItem::getPosition))
				.collect(Collectors.groupingBy(l -> l.getList().getId()));
	}

	/** 토큰 → 화면 이름. 순서를 지키고 같은 토큰은 처음 것만 둔다 */
	private static Map<String, String> tokens(List<IngredientListItem> lines) {
		Map<String, String> tokens = new LinkedHashMap<>();
		for (IngredientListItem line : lines) {
			Long ingredientId = line.getIngredient() == null ? null : line.getIngredient().getId();
			String name = line.getIngredient() == null ? line.getRawText() : line.getIngredient().getStandardName();
			tokens.putIfAbsent(ListFingerprint.token(new FinalItem(ingredientId, line.getRawText())), name);
		}
		return tokens;
	}

	/** 표준명과 원문을 모두 보고 정한 성분 그룹(enum 순서) */
	private static List<String> groups(List<IngredientListItem> lines) {
		List<String> names = new ArrayList<>();
		for (IngredientListItem line : lines) {
			names.add(line.getRawText());
			if (line.getIngredient() != null) {
				names.add(line.getIngredient().getStandardName());
			}
		}
		return IngredientGroup.detect(names).stream().map(Enum::name).toList();
	}
}
