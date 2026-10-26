package com.mycosmetic.service.product.review;

import java.time.Clock;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.EnumMap;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mycosmetic.dto.review.ReviewIssueResponse;
import com.mycosmetic.dto.review.ReviewIssueType;
import com.mycosmetic.dto.review.ReviewMember;
import com.mycosmetic.dto.review.ReviewResponse;
import com.mycosmetic.dto.review.ReviewSuggestion;
import com.mycosmetic.entity.IngredientListItem;
import com.mycosmetic.entity.MatchType;
import com.mycosmetic.entity.Product;
import com.mycosmetic.entity.ProductSource;
import com.mycosmetic.entity.ReviewDismissal;
import com.mycosmetic.entity.User;
import com.mycosmetic.entity.UserRole;
import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.repository.IngredientListItemRepository;
import com.mycosmetic.repository.ProductRepository;
import com.mycosmetic.repository.ProductUsageRepository;
import com.mycosmetic.repository.ReviewDismissalRepository;
import com.mycosmetic.repository.UserRepository;
import com.mycosmetic.service.ingredient.IngredientListRematchService;
import com.mycosmetic.service.ingredient.IngredientListRematchService.RematchReport;
import com.mycosmetic.service.ingredient.MatchResult;
import com.mycosmetic.service.product.ProductQueryService;

/**
 * 관리자 점검함(2026-10-24). 공유 제품 데이터를 규칙으로 점검해 문제 목록을 만든다. 저장하지 않고 매번 계산한다.
 * 관리자만 쓸 수 있고(아니면 FORBIDDEN), 보관한 제품은 점검하지 않는다. 성분은 대표 버전만 본다.
 */
@Service
public class ProductReviewService {

	/** 이보다 성분 줄이 적으면 성분이 덜 들어온 것으로 본다 */
	static final int MIN_INGREDIENT_LINES = 5;
	/** 띄어쓰기로 고칠 수 없는 "사전에 없음" 줄이 이 비율을 넘으면 알린다 */
	static final double UNMATCHED_RATIO = 0.15;
	static final int MAX_EXAMPLES = 3;
	/** 성분이 아니라 사용법·제품 설명에서 나오는 말 */
	private static final Pattern NOT_INGREDIENT = Pattern
			.compile("개월|사용|주세요|합니다|입니다|바르|도포|세안|마무리|\\d+\\s*(?:ml|mL|ML)(?![A-Za-z])");
	private static final int MAX_INGREDIENT_LINE = 80;

	private final UserRepository userRepository;
	private final ProductRepository productRepository;
	private final IngredientListItemRepository itemRepository;
	private final ProductQueryService productQueryService;
	private final ReviewDismissalRepository dismissalRepository;
	private final IngredientListRematchService rematchService;
	private final ProductUsageRepository usageRepository;
	private final Clock clock;

	public ProductReviewService(UserRepository userRepository, ProductRepository productRepository,
			IngredientListItemRepository itemRepository, ProductQueryService productQueryService,
			ReviewDismissalRepository dismissalRepository, IngredientListRematchService rematchService,
			ProductUsageRepository usageRepository, Clock clock) {
		this.usageRepository = usageRepository;
		this.userRepository = userRepository;
		this.productRepository = productRepository;
		this.itemRepository = itemRepository;
		this.productQueryService = productQueryService;
		this.dismissalRepository = dismissalRepository;
		this.rematchService = rematchService;
		this.clock = clock;
	}

	@Transactional(readOnly = true)
	public ReviewResponse review(Long userId) {
		requireAdmin(userId);
		List<Product> products = productRepository.findAll().stream().filter(p -> !p.isArchived())
				.sorted(Comparator.comparing(Product::getId)).toList();
		Map<Long, Long> reps = productQueryService.representativeListIds(products.stream().map(Product::getId).toList());
		Map<Long, List<IngredientListItem>> linesByList = reps.isEmpty() ? Map.of()
				: itemRepository.findWithIngredientByListIdIn(reps.values()).stream()
						.sorted(Comparator.comparingInt(IngredientListItem::getPosition))
						.collect(Collectors.groupingBy(l -> l.getList().getId()));
		Map<IngredientListItem, String> spacingFixes = spacingFixes(linesByList);

		List<ReviewIssueResponse> found = new ArrayList<>();
		for (Product p : products) {
			if (!p.isVerified()) {
				found.add(newUserProduct(p));
			}
			nameFormat(p).ifPresent(found::add);
		}
		found.addAll(brandVariants(products));
		found.addAll(duplicates(products, reps, linesByList));
		for (Product p : products) {
			Long listId = reps.get(p.getId());
			if (listId != null) {
				found.addAll(ingredientIssues(p, listId, linesByList.getOrDefault(listId, List.of()), spacingFixes));
			}
		}

		Map<String, String> dismissed = dismissalRepository.findAll().stream()
				.collect(Collectors.toMap(d -> key(d.getProductId(), d.getIssueType()), ReviewDismissal::getSignature));
		List<ReviewIssueResponse> issues = found.stream()
				.filter(i -> !i.signature().equals(dismissed.get(key(i.productId(), i.type().name()))))
				.sorted(Comparator.comparing(ReviewIssueResponse::type).thenComparing(ReviewIssueResponse::productId))
				.toList();
		Map<ReviewIssueType, Long> counts = new EnumMap<>(ReviewIssueType.class);
		for (ReviewIssueType t : ReviewIssueType.values()) {
			counts.put(t, 0L);
		}
		issues.forEach(i -> counts.merge(i.type(), 1L, Long::sum));
		long spacingLines = spacingFixes.keySet().stream().filter(l -> reps.containsValue(l.getList().getId())).count();
		return new ReviewResponse(products.size(), counts, spacingLines, found.size() - issues.size(), issues);
	}

	/** 같은 문제를 다시 넘기면 값을 바꾼다. 없거나 보관한 제품이면 NOT_FOUND */
	@Transactional
	public void dismiss(Long userId, Long productId, ReviewIssueType type, String signature) {
		requireAdmin(userId);
		productRepository.findById(productId).filter(p -> !p.isArchived())
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
		ReviewDismissal existing = dismissalRepository.findByProductIdAndIssueType(productId, type.name()).orElse(null);
		if (existing == null) {
			dismissalRepository.save(ReviewDismissal.of(productId, type.name(), signature, userId, clock.instant()));
		} else {
			existing.dismissed(signature, userId, clock.instant());
		}
	}

	/** 사용자가 등록한 제품을 확인했다고 표시한다. 없거나 보관한 제품이면 NOT_FOUND */
	@Transactional
	public void verify(Long userId, Long productId) {
		requireAdmin(userId);
		productRepository.findById(productId).filter(p -> !p.isArchived())
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND)).verify();
	}

	/** 모든 성분 버전의 "사전에 없음" 줄을 공백을 빼고 다시 매칭한다. 버전마다 따로 저장된다 */
	public RematchReport fixSpacing(Long userId) {
		requireAdmin(userId);
		return rematchService.rematchWithoutSpaces();
	}

	private static ReviewIssueResponse newUserProduct(Product p) {
		String source = p.getSource() == ProductSource.LINK ? "올리브영 링크에서 가져옴" : "사용자가 직접 입력함";
		return issue(p, ReviewIssueType.NEW_USER_PRODUCT,
				List.of("새로 등록된 제품이에요(" + source + "). 브랜드·제품명·성분이 맞는지 확인해 주세요"),
				List.of("등록일 " + p.getCreatedAt()), null, null, "unverified");
	}

	private Optional<ReviewIssueResponse> nameFormat(Product p) {
		ProductNameRules.Cleaned cleaned = ProductNameRules.clean(p.getBrand(), p.getName());
		if (cleaned.reasons().isEmpty()) {
			return Optional.empty();
		}
		String volume = p.getVolume() == null || p.getVolume().isBlank() ? cleaned.volume() : null;
		return Optional.of(issue(p, ReviewIssueType.NAME_FORMAT,
				cleaned.reasons().stream().map(ProductNameRules.Reason::message).toList(),
				List.of(p.getName() + " → " + cleaned.name()), new ReviewSuggestion(null, cleaned.name(), volume),
				null, p.getName() + "|" + p.getVolume()));
	}

	/** 공백·대소문자만 다른 브랜드 표기들 중 가장 많이 쓴 표기(같으면 먼저 등록된 것)로 맞추자고 한다 */
	private List<ReviewIssueResponse> brandVariants(List<Product> products) {
		List<ReviewIssueResponse> result = new ArrayList<>();
		Map<String, List<Product>> byBrand = products.stream()
				.collect(Collectors.groupingBy(p -> ProductNameRules.squash(p.getBrand()), LinkedHashMap::new,
						Collectors.toList()));
		for (List<Product> group : byBrand.values()) {
			Map<String, Long> spellings = group.stream()
					.collect(Collectors.groupingBy(Product::getBrand, LinkedHashMap::new, Collectors.counting()));
			if (spellings.size() < 2) {
				continue;
			}
			String common = spellings.entrySet().stream().max(Map.Entry.comparingByValue()).orElseThrow().getKey();
			for (Product p : group) {
				if (!p.getBrand().equals(common)) {
					result.add(issue(p, ReviewIssueType.BRAND_VARIANT,
							List.of("같은 브랜드를 다르게 적었어요: " + String.join(", ", spellings.keySet())),
							List.of(p.getBrand() + " → " + common), new ReviewSuggestion(common, null, null), null,
							p.getBrand()));
				}
			}
		}
		return result;
	}

	private List<ReviewIssueResponse> duplicates(List<Product> products, Map<Long, Long> reps,
			Map<Long, List<IngredientListItem>> linesByList) {
		List<ReviewIssueResponse> result = new ArrayList<>();
		Map<String, List<Product>> groups = products.stream()
				.collect(Collectors.groupingBy(
						p -> ProductNameRules.squash(p.getBrand()) + "\n" + ProductNameRules.duplicateKey(p.getBrand(), p.getName()),
						LinkedHashMap::new, Collectors.toList()));
		groups.values().removeIf(group -> group.size() < 2);
		Map<Long, Long> shelfCounts = shelfCounts(
				groups.values().stream().flatMap(List::stream).map(Product::getId).toList());
		for (List<Product> group : groups.values()) {
			// 묶음 하나에 문제 하나: 가장 먼저 등록된 제품에 붙이고, 묶음 제품을 나란히 보여 준다
			String signature = group.stream().map(p -> String.valueOf(p.getId())).collect(Collectors.joining(","));
			List<ReviewMember> members = group.stream().map(p -> {
				Long listId = reps.get(p.getId());
				int lines = listId == null ? 0 : linesByList.getOrDefault(listId, List.of()).size();
				return new ReviewMember(p.getId(), p.getBrand(), p.getName(), p.getVolume(), p.getCreatedAt(),
						shelfCounts.getOrDefault(p.getId(), 0L), lines);
			}).toList();
			result.add(issue(group.get(0), ReviewIssueType.DUPLICATE_SUSPECT,
					List.of("이름이 거의 같은 제품이 " + group.size() + "개 있어요. 같은 제품이면 하나만 남기고 삭제하세요"),
					List.of(), null, members, signature));
		}
		return result;
	}

	/** 제품별 화장대 항목 수. 어느 중복 제품을 남길지 고를 때 본다 */
	private Map<Long, Long> shelfCounts(List<Long> productIds) {
		if (productIds.isEmpty()) {
			return Map.of();
		}
		Map<Long, Long> counts = new HashMap<>();
		for (Object[] row : usageRepository.countShelfItemsByProductIdIn(productIds)) {
			counts.put((Long) row[0], (Long) row[1]);
		}
		return counts;
	}

	private List<ReviewIssueResponse> ingredientIssues(Product p, Long listId, List<IngredientListItem> lines,
			Map<IngredientListItem, String> spacingFixes) {
		List<ReviewIssueResponse> result = new ArrayList<>();
		String signature = "list:" + listId;
		List<IngredientListItem> fixable = lines.stream().filter(spacingFixes::containsKey).toList();
		if (!fixable.isEmpty()) {
			result.add(issue(p, ReviewIssueType.INGREDIENT_SPACING,
					List.of("띄어쓰기만 고치면 사전에 맞는 줄이 " + fixable.size() + "개 있어요"),
					fixable.stream().limit(MAX_EXAMPLES).map(l -> l.getRawText() + " → " + spacingFixes.get(l)).toList(),
					null, null, signature + ":" + fixable.size()));
		}
		List<IngredientListItem> sentences = lines.stream().filter(l -> l.getMatchType() == MatchType.UNMATCHED)
				.filter(l -> l.getRawText().length() > MAX_INGREDIENT_LINE || NOT_INGREDIENT.matcher(l.getRawText()).find())
				.toList();
		if (!sentences.isEmpty() || lines.size() < MIN_INGREDIENT_LINES) {
			List<String> reasons = new ArrayList<>();
			if (!sentences.isEmpty()) {
				reasons.add("성분이 아닌 글(사용법·용량 등)이 " + sentences.size() + "줄 섞였어요");
			}
			if (lines.size() < MIN_INGREDIENT_LINES) {
				reasons.add("성분이 " + lines.size() + "줄뿐이에요");
			}
			result.add(issue(p, ReviewIssueType.INGREDIENT_NOT_LIST, reasons,
					sentences.stream().limit(MAX_EXAMPLES).map(l -> shorten(l.getRawText())).toList(), null, null,
					signature));
		}
		long remaining = lines.stream().filter(l -> l.getMatchType() == MatchType.UNMATCHED)
				.filter(l -> !spacingFixes.containsKey(l)).count();
		if (lines.size() >= MIN_INGREDIENT_LINES && remaining > lines.size() * UNMATCHED_RATIO) {
			result.add(issue(p, ReviewIssueType.INGREDIENT_UNMATCHED,
					List.of("사전에 없는 줄이 " + remaining + "개예요(전체 " + lines.size() + "줄). 오타인지 확인해 주세요"),
					lines.stream().filter(l -> l.getMatchType() == MatchType.UNMATCHED).filter(l -> !spacingFixes.containsKey(l))
							.limit(MAX_EXAMPLES).map(l -> shorten(l.getRawText())).toList(),
					null, null, signature + ":" + remaining));
		}
		return result;
	}

	/** 공백이 든 "사전에 없음" 줄 중 공백을 빼면 사전에 하나로 정해지는 줄 → 맞는 표준명. 모두 한 번에 매칭한다 */
	private Map<IngredientListItem, String> spacingFixes(Map<Long, List<IngredientListItem>> linesByList) {
		List<IngredientListItem> candidates = linesByList.values().stream().flatMap(List::stream)
				.filter(l -> l.getMatchType() == MatchType.UNMATCHED && l.getRawText().strip().contains(" "))
				.toList();
		if (candidates.isEmpty()) {
			return Map.of();
		}
		List<MatchResult> results = rematchService
				.matchWithoutSpaces(candidates.stream().map(IngredientListItem::getRawText).toList());
		Map<IngredientListItem, String> fixes = new HashMap<>();
		for (int i = 0; i < candidates.size(); i++) {
			if (results.get(i).ingredientId() != null) {
				fixes.put(candidates.get(i), results.get(i).standardName());
			}
		}
		return fixes;
	}

	private static ReviewIssueResponse issue(Product p, ReviewIssueType type, List<String> reasons,
			List<String> examples, ReviewSuggestion suggestion, List<ReviewMember> members, String signature) {
		return new ReviewIssueResponse(p.getId(), p.getBrand(), p.getName(), p.getVolume(), p.getCategory(), type,
				reasons, examples, suggestion, members, shortenSignature(signature));
	}

	private static String shorten(String text) {
		return text.length() <= 40 ? text : text.substring(0, 40) + "…";
	}

	/** DB 칸(300자)에 맞춘다 */
	private static String shortenSignature(String s) {
		return s.length() <= 300 ? s : s.substring(0, 300);
	}

	private static String key(Long productId, String type) {
		return productId + ":" + type;
	}

	private User requireAdmin(Long userId) {
		User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
		if (user.getRole() != UserRole.ADMIN) {
			throw new BusinessException(ErrorCode.FORBIDDEN);
		}
		return user;
	}
}
