package com.mycosmetic.service.product.seed;

import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import com.mycosmetic.dto.product.FinalItem;
import com.mycosmetic.entity.Category;
import com.mycosmetic.entity.Ingredient;
import com.mycosmetic.entity.IngredientList;
import com.mycosmetic.entity.IngredientListItem;
import com.mycosmetic.entity.MatchType;
import com.mycosmetic.entity.Product;
import com.mycosmetic.repository.IngredientListItemRepository;
import com.mycosmetic.repository.IngredientListRepository;
import com.mycosmetic.repository.IngredientRepository;
import com.mycosmetic.repository.ProductRepository;
import com.mycosmetic.service.product.ListFingerprint;

/**
 * 시드 한 줄을 하나의 트랜잭션으로 넣는다. 줄마다 새 트랜잭션이라 한 줄이 실패해도 앞서 넣은 줄은 남는다.
 * 만든 사람(created_by)은 null, 확인 0회, 화장대 항목은 만들지 않는다.
 */
@Service
public class ProductSeedTxService {

	/** 한 줄의 결과 */
	public enum Outcome {
		/** 제품과 성분 버전을 새로 만들었다(만들 예정) */
		CREATED_PRODUCT,
		/** 기존 제품에 새 성분 버전을 더했다(더할 예정) */
		CREATED_LIST,
		/** 같은 제품에 같은 fingerprint 버전이 이미 있다 */
		ALREADY_EXISTS,
		/** 같은 브랜드+제품명이 보관(관리자 삭제)된 제품뿐이다. 되살리지 않는다 */
		ARCHIVED
	}

	/**
	 * 넣을 성분 한 줄. ingredientId는 EXACT·ALIAS일 때만 있다
	 *
	 * @param matchType 저장할 매칭 종류. 성분이 없으면 UNMATCHED
	 */
	public record SeedItem(Long ingredientId, String rawText, MatchType matchType) {
	}

	/** volume이 비었으면 null */
	public record SeedProduct(String brand, String name, String volume, Category category) {
	}

	private final ProductRepository productRepository;
	private final IngredientRepository ingredientRepository;
	private final IngredientListRepository listRepository;
	private final IngredientListItemRepository itemRepository;

	public ProductSeedTxService(ProductRepository productRepository, IngredientRepository ingredientRepository,
			IngredientListRepository listRepository, IngredientListItemRepository itemRepository) {
		this.productRepository = productRepository;
		this.ingredientRepository = ingredientRepository;
		this.listRepository = listRepository;
		this.itemRepository = itemRepository;
	}

	/** 아무것도 쓰지 않고 넣으면 어떻게 될지만 본다 */
	@Transactional(propagation = Propagation.REQUIRES_NEW, readOnly = true)
	public Outcome preview(SeedProduct info, List<SeedItem> items) {
		Optional<Product> product = activeProduct(info);
		if (product.isEmpty()) {
			return hasArchived(info) ? Outcome.ARCHIVED : Outcome.CREATED_PRODUCT;
		}
		return listRepository.findIdByProductIdAndFingerprint(product.get().getId(), fingerprint(items)).isPresent()
				? Outcome.ALREADY_EXISTS
				: Outcome.CREATED_LIST;
	}

	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public Outcome insert(SeedProduct info, List<SeedItem> items) {
		Optional<Product> existing = activeProduct(info);
		if (existing.isEmpty() && hasArchived(info)) {
			return Outcome.ARCHIVED;
		}
		String fingerprint = fingerprint(items);
		if (existing.isPresent()
				&& listRepository.findIdByProductIdAndFingerprint(existing.get().getId(), fingerprint).isPresent()) {
			return Outcome.ALREADY_EXISTS;
		}
		Product product = existing.orElseGet(() -> productRepository.saveAndFlush(
				Product.create(null, info.brand(), info.name(), info.volume(), info.category(), null)));

		Map<Long, Ingredient> ingredients = loadIngredients(items);
		IngredientList list = listRepository.saveAndFlush(IngredientList.create(product, fingerprint, null));
		List<IngredientListItem> rows = new ArrayList<>(items.size());
		for (int i = 0; i < items.size(); i++) {
			SeedItem item = items.get(i);
			Ingredient ingredient = item.ingredientId() == null ? null : ingredients.get(item.ingredientId());
			rows.add(IngredientListItem.create(list, i + 1, ingredient, item.rawText(),
					ingredient == null ? MatchType.UNMATCHED : item.matchType()));
		}
		itemRepository.saveAll(rows);
		itemRepository.flush();
		return existing.isPresent() ? Outcome.CREATED_LIST : Outcome.CREATED_PRODUCT;
	}

	/** 같은 브랜드+제품명 중 보관하지 않은 가장 오래된 제품 */
	private Optional<Product> activeProduct(SeedProduct info) {
		return productRepository.findByBrandAndNameOrderByIdAsc(info.brand(), info.name()).stream()
				.filter(p -> !p.isArchived())
				.findFirst();
	}

	private boolean hasArchived(SeedProduct info) {
		return productRepository.findByBrandAndNameOrderByIdAsc(info.brand(), info.name()).stream()
				.anyMatch(Product::isArchived);
	}

	private static String fingerprint(List<SeedItem> items) {
		return ListFingerprint.of(items.stream().map(i -> new FinalItem(i.ingredientId(), i.rawText())).toList());
	}

	private Map<Long, Ingredient> loadIngredients(List<SeedItem> items) {
		Set<Long> ids = items.stream()
				.map(SeedItem::ingredientId)
				.filter(id -> id != null)
				.collect(Collectors.toCollection(LinkedHashSet::new));
		if (ids.isEmpty()) {
			return Map.of();
		}
		return ingredientRepository.findAllById(ids).stream()
				.collect(Collectors.toMap(Ingredient::getId, Function.identity()));
	}
}
