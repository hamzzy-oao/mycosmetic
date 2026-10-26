package com.mycosmetic.service.advice;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

import org.springframework.stereotype.Component;

import com.mycosmetic.entity.IngredientListItem;
import com.mycosmetic.entity.ShelfItem;
import com.mycosmetic.repository.IngredientListItemRepository;

/**
 * 화장대 항목들의 성분(내가 담은 버전)을 한 번에 읽어 판단용 {@link ShelfEntry}로 만든다.
 * "이거 사도 돼?"와 피부 분석·화장대 분석이 같이 쓴다. 호출하는 쪽의 트랜잭션 안에서 부른다
 */
@Component
public class ShelfEntryLoader {

	/**
	 * @param lines             버전 ID별 성분 줄(순서대로)
	 * @param ingredientsByList 버전 ID별 판단용 성분
	 * @param entries           화장대 항목 순서 그대로
	 */
	public record Loaded(Map<Long, List<IngredientListItem>> lines, Map<Long, List<AdviceIngredient>> ingredientsByList,
			List<ShelfEntry> entries) {
	}

	private final IngredientListItemRepository listItemRepository;

	public ShelfEntryLoader(IngredientListItemRepository listItemRepository) {
		this.listItemRepository = listItemRepository;
	}

	/** @param extraListId 화장대 밖의 버전도 함께 읽을 때 그 ID(없으면 null) */
	public Loaded load(List<ShelfItem> shelf, Long extraListId) {
		Map<Long, List<IngredientListItem>> lines = linesByList(shelf, extraListId);
		Map<Long, List<AdviceIngredient>> ingredientsByList = new HashMap<>();
		lines.forEach((listId, l) -> ingredientsByList.put(listId, toIngredients(l)));
		List<ShelfEntry> entries = shelf.stream()
				.map(item -> new ShelfEntry(item.getId(), item.getProduct().getId(), item.getVerdict(),
						ingredientsByList.getOrDefault(item.getIngredientList().getId(), List.of())))
				.toList();
		return new Loaded(lines, ingredientsByList, entries);
	}

	/** 화장대 항목의 버전들과(있으면) 대상 버전의 성분 줄을 한 번에 읽어 버전별·순서대로 나눈다 */
	private Map<Long, List<IngredientListItem>> linesByList(List<ShelfItem> shelf, Long extraListId) {
		Set<Long> ids = new HashSet<>();
		shelf.forEach(item -> ids.add(item.getIngredientList().getId()));
		if (extraListId != null) {
			ids.add(extraListId);
		}
		if (ids.isEmpty()) {
			return Map.of();
		}
		return listItemRepository.findWithIngredientByListIdIn(ids).stream()
				.sorted(Comparator.comparingInt(IngredientListItem::getPosition))
				.collect(Collectors.groupingBy(l -> l.getList().getId()));
	}

	static List<AdviceIngredient> toIngredients(List<IngredientListItem> lines) {
		List<AdviceIngredient> ingredients = new ArrayList<>(lines.size());
		for (IngredientListItem line : lines) {
			ingredients.add(line.getIngredient() == null ? AdviceIngredient.of(null, null, line.getRawText())
					: AdviceIngredient.of(line.getIngredient().getId(), line.getIngredient().getStandardName(),
							line.getRawText()));
		}
		return ingredients;
	}
}
