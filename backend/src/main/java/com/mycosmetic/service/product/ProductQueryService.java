package com.mycosmetic.service.product;

import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mycosmetic.dto.product.ProductDetailResponse;
import com.mycosmetic.dto.product.ProductDetailResponse.ItemView;
import com.mycosmetic.dto.product.ProductDetailResponse.ListBrief;
import com.mycosmetic.dto.product.ProductDetailResponse.ListView;
import com.mycosmetic.dto.product.ProductSummaryResponse;
import com.mycosmetic.entity.IngredientList;
import com.mycosmetic.entity.IngredientListItem;
import com.mycosmetic.entity.Product;
import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.repository.IngredientListItemRepository;
import com.mycosmetic.repository.IngredientListRepository;
import com.mycosmetic.repository.ProductRepository;

/** 제품 조회: 바코드, 이름 검색, 상세, 특정 버전 (설계 4.1 1단계, 4.6). 🌐 공유 데이터라 사용자 ID로 거르지 않는다 */
@Service
@Transactional(readOnly = true)
public class ProductQueryService {

	static final int SEARCH_LIMIT = 20;
	static final int MIN_QUERY_LENGTH = 2;

	/** JPQL like의 escape 문자. MySQL에서 백슬래시는 문자열 안 escape라 쓰지 않는다 */
	static final char LIKE_ESCAPE = '!';

	private static final Comparator<IngredientList> NEWEST_FIRST = Comparator
			.comparing(IngredientList::getCreatedAt, Comparator.nullsLast(Comparator.reverseOrder()))
			.thenComparing(IngredientList::getId, Comparator.reverseOrder());

	private final ProductRepository productRepository;
	private final IngredientListRepository listRepository;
	private final IngredientListItemRepository itemRepository;

	public ProductQueryService(ProductRepository productRepository, IngredientListRepository listRepository,
			IngredientListItemRepository itemRepository) {
		this.productRepository = productRepository;
		this.listRepository = listRepository;
		this.itemRepository = itemRepository;
	}

	/** 브랜드·제품명 부분 일치, 최대 20개. 검색어가 2글자 미만이면 빈 목록. %와 _는 글자 그대로 찾는다 */
	public List<ProductSummaryResponse> search(String q) {
		String query = q == null ? "" : q.strip();
		if (query.length() < MIN_QUERY_LENGTH) {
			return List.of();
		}
		List<Product> products = productRepository.searchByBrandOrName(escapeLike(query),
				PageRequest.of(0, SEARCH_LIMIT));
		return summaries(products);
	}

	/** 대표 버전(성분 줄 포함)과 나머지 버전 목록(최신순) */
	public ProductDetailResponse detail(Long productId) {
		Product product = productRepository.findById(productId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
		List<IngredientList> lists = listRepository.findByProductId(productId);
		IngredientList representative = ListVersionRules.representative(lists).orElse(null);

		ListView representativeView = representative == null ? null : toView(representative);
		List<ListBrief> others = lists.stream()
				.filter(l -> representative == null || !l.getId().equals(representative.getId()))
				.sorted(NEWEST_FIRST)
				.map(l -> new ListBrief(l.getId(), l.getStatus(), l.getConfirmCount(), l.getCreatedAt()))
				.toList();
		Long representativeId = representative == null ? null : representative.getId();
		return new ProductDetailResponse(ProductSummaryResponse.of(product, representativeId), representativeView,
				others);
	}

	/** 특정 성분 버전(성분 줄 포함). 화장대에서 내가 고른 버전을 보여 줄 때 쓴다. 없거나 그 제품의 것이 아니면 NOT_FOUND */
	public ListView list(Long productId, Long listId) {
		IngredientList list = listRepository.findById(listId)
				.filter(l -> l.getProduct().getId().equals(productId))
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
		return toView(list);
	}

	/** 제품별 대표 버전 ID. 버전이 없는 제품은 맵에 없다. 화장대 목록도 이 규칙을 쓴다 */
	public Map<Long, Long> representativeListIds(Collection<Long> productIds) {
		if (productIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, List<IngredientList>> byProduct = listRepository.findByProductIdIn(productIds).stream()
				.collect(Collectors.groupingBy(l -> l.getProduct().getId()));
		Map<Long, Long> result = new HashMap<>();
		byProduct.forEach((productId, lists) -> ListVersionRules.representative(lists)
				.ifPresent(rep -> result.put(productId, rep.getId())));
		return result;
	}

	private List<ProductSummaryResponse> summaries(List<Product> products) {
		Map<Long, Long> reps = representativeListIds(products.stream().map(Product::getId).toList());
		return products.stream()
				.map(p -> ProductSummaryResponse.of(p, reps.get(p.getId())))
				.toList();
	}

	private ListView toView(IngredientList list) {
		List<ItemView> items = itemRepository.findWithIngredientByListId(list.getId()).stream()
				.map(ProductQueryService::toItemView)
				.toList();
		return new ListView(list.getId(), list.getStatus(), list.getConfirmCount(), items);
	}

	private static ItemView toItemView(IngredientListItem item) {
		if (item.getIngredient() == null) {
			return new ItemView(item.getPosition(), null, item.getRawText(), item.getMatchType());
		}
		return new ItemView(item.getPosition(), item.getIngredient().getId(), item.getIngredient().getStandardName(),
				item.getMatchType());
	}

	static String escapeLike(String q) {
		StringBuilder sb = new StringBuilder(q.length());
		for (char c : q.toCharArray()) {
			if (c == LIKE_ESCAPE || c == '%' || c == '_') {
				sb.append(LIKE_ESCAPE);
			}
			sb.append(c);
		}
		return sb.toString();
	}
}
