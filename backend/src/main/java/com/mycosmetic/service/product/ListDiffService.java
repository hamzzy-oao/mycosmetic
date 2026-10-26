package com.mycosmetic.service.product;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mycosmetic.dto.product.FinalItem;
import com.mycosmetic.dto.product.ListDiffResponse;
import com.mycosmetic.entity.IngredientList;
import com.mycosmetic.entity.IngredientListItem;
import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.repository.IngredientListItemRepository;
import com.mycosmetic.repository.IngredientListRepository;

/**
 * 같은 제품의 두 성분 버전 차이 (설계 4.6). 성분은 fingerprint와 같은 토큰({@link ListFingerprint#token})으로 비교한다.
 */
@Service
@Transactional(readOnly = true)
public class ListDiffService {

	private final IngredientListRepository listRepository;
	private final IngredientListItemRepository itemRepository;

	public ListDiffService(IngredientListRepository listRepository, IngredientListItemRepository itemRepository) {
		this.listRepository = listRepository;
		this.itemRepository = itemRepository;
	}

	/** from → to. 두 버전 중 하나라도 없거나 그 제품의 것이 아니면 NOT_FOUND */
	public ListDiffResponse diff(Long productId, Long fromListId, Long toListId) {
		Map<String, String> from = tokens(productId, fromListId);
		Map<String, String> to = tokens(productId, toListId);

		List<String> added = to.entrySet().stream()
				.filter(e -> !from.containsKey(e.getKey()))
				.map(Map.Entry::getValue)
				.toList();
		List<String> removed = from.entrySet().stream()
				.filter(e -> !to.containsKey(e.getKey()))
				.map(Map.Entry::getValue)
				.toList();
		List<String> commonInFrom = new ArrayList<>(from.keySet());
		commonInFrom.retainAll(to.keySet());
		List<String> commonInTo = new ArrayList<>(to.keySet());
		commonInTo.retainAll(from.keySet());
		return new ListDiffResponse(added, removed, !commonInFrom.equals(commonInTo));
	}

	/** 토큰 → 화면 이름(표준명, 매칭 안 됐으면 원문). 순서를 지키고 같은 토큰은 처음 것만 둔다 */
	private Map<String, String> tokens(Long productId, Long listId) {
		IngredientList list = listRepository.findById(listId)
				.filter(l -> l.getProduct().getId().equals(productId))
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
		Map<String, String> tokens = new LinkedHashMap<>();
		for (IngredientListItem item : itemRepository.findWithIngredientByListId(list.getId())) {
			Long ingredientId = item.getIngredient() == null ? null : item.getIngredient().getId();
			String token = ListFingerprint.token(new FinalItem(ingredientId, item.getRawText()));
			String name = item.getIngredient() == null ? item.getRawText() : item.getIngredient().getStandardName();
			tokens.putIfAbsent(token, name);
		}
		return tokens;
	}
}
