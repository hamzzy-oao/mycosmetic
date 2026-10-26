package com.mycosmetic.service.product.review;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mycosmetic.dto.product.NameCheckResponse;
import com.mycosmetic.dto.product.NameCheckResponse.SimilarProduct;
import com.mycosmetic.dto.product.ProductSummaryResponse;
import com.mycosmetic.entity.Product;
import com.mycosmetic.entity.ProductSource;
import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.repository.ProductLookupRepository;
import com.mycosmetic.service.ingredient.NameSimilarity;
import com.mycosmetic.service.product.ProductQueryService;

/**
 * 사용자가 새 제품을 직접 등록할 때 이름을 점검한다(2026-10-26 제품명 예외처리).
 * <ul>
 * <li>올리브영식 표기 규칙({@link ProductNameRules})으로 이름을 정리해 보여 준다(브랜드·용량·판매 문구 빼기)</li>
 * <li>브랜드가 등록된 표기와 다르거나(공백·대소문자) 처음 보는 브랜드인데 비슷한 브랜드가 있으면 그 표기를 제안한다</li>
 * <li>같은 브랜드에서 이름이 같거나 비슷한 제품을 먼저 보여 준다(오타·중복 막기)</li>
 * </ul>
 * 제품 정보는 🌐 공유 데이터라 로그인한 누구나 쓸 수 있다. 보관한 제품은 빼고 본다.
 */
@Service
public class ProductNameCheckService {

	/** 이보다 비슷하면(정리한 이름의 편집 거리 기준) 같은 제품일 수 있다고 보여 준다 */
	static final double SIMILAR_NAME = 0.7;
	/** 처음 보는 브랜드가 이보다 비슷하면 그 브랜드를 제안한다(예: 설화쑤 → 설화수) */
	static final double SIMILAR_BRAND = 0.6;
	static final int MAX_SIMILAR = 5;
	static final int MAX_BRANDS = 2000;
	static final int MAX_INPUT = 200;

	private final ProductLookupRepository lookupRepository;
	private final ProductQueryService productQueryService;

	public ProductNameCheckService(ProductLookupRepository lookupRepository, ProductQueryService productQueryService) {
		this.lookupRepository = lookupRepository;
		this.productQueryService = productQueryService;
	}

	/** 브랜드·제품명이 비었거나 너무 길면 INVALID_REQUEST */
	@Transactional(readOnly = true)
	public NameCheckResponse check(String brand, String name, String volume) {
		if (brand == null || brand.isBlank() || name == null || name.isBlank() || brand.length() > MAX_INPUT
				|| name.length() > MAX_INPUT) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST);
		}
		String b = brand.strip();
		ProductNameRules.Cleaned cleaned = ProductNameRules.clean(b, name.strip());
		String suggestedVolume = volume == null || volume.isBlank() ? cleaned.volume() : null;

		List<Product> products = lookupRepository.findByArchivedFalse();
		Map<String, Map<String, Long>> spellings = spellingsByBrand(products);
		String key = ProductNameRules.squash(b);
		boolean known = spellings.containsKey(key);
		String brandSuggestion = known ? commonSpelling(spellings.get(key), b) : nearestBrand(spellings, key);
		// 비슷한 제품은 등록된(또는 제안한) 브랜드 안에서 찾는다
		String brandKey = brandSuggestion != null ? ProductNameRules.squash(brandSuggestion) : key;
		List<SimilarProduct> similar = similar(products, brandKey, cleaned.name());

		return new NameCheckResponse(cleaned.name(), suggestedVolume,
				cleaned.reasons().stream().map(ProductNameRules.Reason::message).toList(), known, brandSuggestion,
				similar);
	}

	/** 등록된 브랜드 표기들, 제품이 많은 순(같으면 가나다순). 브랜드 입력 자동완성에 쓴다 */
	@Transactional(readOnly = true)
	public List<String> brands() {
		return lookupRepository.countByBrand().stream()
				.sorted(Comparator.comparing((Object[] row) -> (Long) row[1]).reversed()
						.thenComparing(row -> (String) row[0]))
				.map(row -> (String) row[0]).limit(MAX_BRANDS).toList();
	}

	/** 같은 브랜드에 정리한 이름이 같은 제품(리필·공백·용량 무시). 새 제품 저장 전에 중복을 막을 때 쓴다 */
	@Transactional(readOnly = true)
	public Optional<Product> sameNameProduct(String brand, String name) {
		String brandKey = ProductNameRules.squash(brand);
		String nameKey = ProductNameRules.duplicateKey(brand, name);
		return lookupRepository.findByArchivedFalse().stream()
				.filter(p -> ProductNameRules.squash(p.getBrand()).equals(brandKey))
				.filter(p -> ProductNameRules.duplicateKey(p.getBrand(), p.getName()).equals(nameKey))
				.min(Comparator.comparing(Product::getId));
	}

	/**
	 * 방금 사용자가 만든 제품이 올리브영 링크의 이름 그대로면 출처를 LINK로 바꾼다(검수된 것으로 본다).
	 * 이미 다른 출처이거나 다른 사람이 만든 제품은 그대로 둔다
	 */
	@Transactional
	public void markFromLink(Long productId, Long userId) {
		lookupRepository.findById(productId)
				.filter(p -> p.getSource() == ProductSource.USER && !p.isVerified())
				.filter(p -> p.getCreatedBy() != null && p.getCreatedBy().getId().equals(userId))
				.ifPresent(Product::fromLink);
	}

	/** 쇼핑몰 표기 이름과 사용자가 저장하려는 이름이 같은 제품인가(표기 규칙·리필·공백 무시) */
	public static boolean sameAsLinkName(String brand, String name, String linkName) {
		return linkName != null && ProductNameRules.duplicateKey(brand, name)
				.equals(ProductNameRules.duplicateKey(brand, linkName));
	}

	private List<SimilarProduct> similar(List<Product> products, String brandKey, String cleanedName) {
		String nameKey = ProductNameRules.squash(cleanedName).replace("리필", "");
		List<Product> sameBrand = products.stream()
				.filter(p -> ProductNameRules.squash(p.getBrand()).equals(brandKey)).toList();
		record Scored(Product product, double score, boolean same) {
		}
		List<Scored> hits = sameBrand.stream().map(p -> {
			String other = ProductNameRules.duplicateKey(p.getBrand(), p.getName());
			boolean same = other.equals(nameKey);
			return new Scored(p, same ? 1.0 : NameSimilarity.of(nameKey, other), same);
		}).filter(s -> s.same() || s.score() >= SIMILAR_NAME)
				.sorted(Comparator.comparing(Scored::score).reversed().thenComparing(s -> s.product().getId()))
				.limit(MAX_SIMILAR).toList();
		if (hits.isEmpty()) {
			return List.of();
		}
		Map<Long, Long> reps = productQueryService
				.representativeListIds(hits.stream().map(s -> s.product().getId()).toList());
		return hits.stream().map(s -> new SimilarProduct(
				ProductSummaryResponse.of(s.product(), reps.get(s.product().getId())),
				(int) Math.round(s.score() * 100), s.same())).toList();
	}

	/** 공백·대소문자를 뺀 브랜드 → {표기: 제품 수} */
	private static Map<String, Map<String, Long>> spellingsByBrand(List<Product> products) {
		return products.stream().collect(Collectors.groupingBy(p -> ProductNameRules.squash(p.getBrand()),
				LinkedHashMap::new, Collectors.groupingBy(Product::getBrand, LinkedHashMap::new, Collectors.counting())));
	}

	/** 등록된 표기 중 가장 많이 쓴 것. 입력과 같으면 null(제안할 게 없다) */
	private static String commonSpelling(Map<String, Long> spellings, String input) {
		String common = spellings.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
		return common.equals(input) ? null : common;
	}

	/** 처음 보는 브랜드면 가장 비슷한 등록 브랜드(SIMILAR_BRAND 이상). 없으면 null */
	private static String nearestBrand(Map<String, Map<String, Long>> spellings, String key) {
		return spellings.entrySet().stream()
				.map(e -> Map.entry(e, NameSimilarity.of(key, e.getKey())))
				.filter(e -> e.getValue() >= SIMILAR_BRAND)
				.max(Map.Entry.comparingByValue())
				.map(e -> commonSpelling(e.getKey().getValue(), ""))
				.orElse(null);
	}
}
