package com.mycosmetic.service.product;

import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
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

import com.mycosmetic.dto.product.ConfirmRequest;
import com.mycosmetic.dto.product.ConfirmRequest.NewProduct;
import com.mycosmetic.dto.product.ConfirmResponse;
import com.mycosmetic.dto.product.FinalItem;
import com.mycosmetic.entity.ExtractionJob;
import com.mycosmetic.entity.Ingredient;
import com.mycosmetic.entity.IngredientAlias;
import com.mycosmetic.entity.IngredientList;
import com.mycosmetic.entity.IngredientListItem;
import com.mycosmetic.entity.JobStatus;
import com.mycosmetic.entity.ListConfirmation;
import com.mycosmetic.entity.MatchType;
import com.mycosmetic.entity.Product;
import com.mycosmetic.entity.ShelfItem;
import com.mycosmetic.entity.User;
import com.mycosmetic.entity.Verdict;
import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.repository.ExtractionJobRepository;
import com.mycosmetic.repository.IngredientAliasRepository;
import com.mycosmetic.repository.IngredientListItemRepository;
import com.mycosmetic.repository.IngredientListRepository;
import com.mycosmetic.repository.IngredientRepository;
import com.mycosmetic.repository.ListConfirmationRepository;
import com.mycosmetic.repository.ProductRepository;
import com.mycosmetic.repository.ShelfItemRepository;
import com.mycosmetic.repository.UserRepository;
import com.mycosmetic.service.extraction.ExtractionService;
import com.mycosmetic.service.ingredient.IngredientNameNormalizer;

/**
 * 확인·저장 한 번을 하나의 트랜잭션으로 처리한다 (설계 4.1 9단계).
 * 중복 불가 제약 위반(DataIntegrityViolationException)은 그대로 던지고, {@link ProductConfirmService}가 1회 다시 부른다.
 *
 * <p>잠금 순서: job 행 → (새 제품·새 버전 INSERT) → 버전 행. 버전 행은 확인을 넣기 <b>전에</b> 잠근다.
 * list_confirmations INSERT가 외래키 확인으로 버전 행에 공유 잠금을 걸기 때문에, 확인을 먼저 넣고 나중에 잠그면
 * 두 사용자가 서로의 공유 잠금을 기다리는 교착이 생긴다.
 *
 * <p>한도에서 뺐던 FAILED job(직접 붙여넣기)을 저장하면 하루 한도에 1회로 센다(2026-10-22 사용자 결정 A).
 * 이때만 job 행 다음에 사용자 행을 잠근다. 사용자 행을 먼저 잡고 job 행을 기다리는 경로가 없어 교착이 생기지 않는다
 * (접수는 사용자 행 → 새 job INSERT라 기존 job 행을 기다리지 않고, 탈퇴는 job 행 → 사용자 행 순서로 같다).
 */
@Service
public class ProductConfirmTxService {

	private final ExtractionJobRepository jobRepository;
	private final ProductRepository productRepository;
	private final IngredientRepository ingredientRepository;
	private final IngredientAliasRepository aliasRepository;
	private final IngredientListRepository listRepository;
	private final IngredientListItemRepository itemRepository;
	private final ListConfirmationRepository confirmationRepository;
	private final ShelfItemRepository shelfItemRepository;
	private final UserRepository userRepository;
	private final Clock clock;

	public ProductConfirmTxService(ExtractionJobRepository jobRepository, ProductRepository productRepository,
			IngredientRepository ingredientRepository, IngredientAliasRepository aliasRepository,
			IngredientListRepository listRepository, IngredientListItemRepository itemRepository,
			ListConfirmationRepository confirmationRepository, ShelfItemRepository shelfItemRepository,
			UserRepository userRepository, Clock clock) {
		this.jobRepository = jobRepository;
		this.productRepository = productRepository;
		this.ingredientRepository = ingredientRepository;
		this.aliasRepository = aliasRepository;
		this.listRepository = listRepository;
		this.itemRepository = itemRepository;
		this.confirmationRepository = confirmationRepository;
		this.shelfItemRepository = shelfItemRepository;
		this.userRepository = userRepository;
		this.clock = clock;
	}

	/**
	 * job 확인 → 제품 찾기·만들기 → fingerprint → 버전 찾기·만들기 → 확인 추가와 상태 갱신 → 화장대에 담기 → job 결과 삭제.
	 * 다시 부를 때 앞선 시도의 영속성 컨텍스트를 쓰지 않도록 항상 새 트랜잭션으로 연다.
	 */
	@Transactional(propagation = Propagation.REQUIRES_NEW)
	public ConfirmResponse confirmOnce(Long userId, ConfirmRequest request) {
		ExtractionJob job = jobRepository.findWithLockByIdAndUserId(request.jobId(), userId)
				.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
		if (!job.isFinished()) {
			throw new BusinessException(ErrorCode.EXTRACTION_NOT_READY);
		}
		if (job.isConfirmed()) {
			throw new BusinessException(ErrorCode.EXTRACTION_ALREADY_CONFIRMED);
		}
		if (job.getStatus() == JobStatus.FAILED && !job.isCountsTowardLimit()) {
			countPastedConfirmTowardLimit(userId, job);
		}
		User user = job.getUser();
		List<FinalItem> items = request.items();
		Map<Long, Ingredient> ingredients = loadIngredients(items);

		Product product = findOrCreateProduct(request, user);
		String fingerprint = ListFingerprint.of(items);
		IngredientList list = findOrCreateList(product, fingerprint, items, ingredients, user);

		if (!confirmationRepository.existsByListIdAndUserId(list.getId(), userId)) {
			confirmationRepository.saveAndFlush(ListConfirmation.create(list, user));
			list.addConfirmation();
		}

		ShelfItem shelfItem = shelfItemRepository.findByUserIdAndIngredientListId(userId, list.getId())
				.orElseGet(() -> {
					ShelfItem created = ShelfItem.create(user, product, list, Verdict.UNRATED, null, null, null);
					// 개봉일은 사용자가 열 때 넣는다. 상세페이지에서 읽은 개봉 후 사용기간만 미리 둔다
					created.changeOpening(null, request.paoMonths());
					return shelfItemRepository.saveAndFlush(created);
				});

		job.markConfirmed(Instant.now(clock));
		return new ConfirmResponse(product.getId(), list.getId(), list.getStatus(), shelfItem.getId());
	}

	/**
	 * 직접 붙여넣기 저장을 하루 한도에 센다. 접수와 같이 사용자 행을 잠근 뒤 오늘(Asia/Seoul) 센 job 수를 보고,
	 * 20 이상이면 아무것도 저장하지 않고 EXTRACTION_DAILY_LIMIT. DONE job과 이미 센 FAILED job은 다시 세지 않는다
	 */
	private void countPastedConfirmTowardLimit(Long userId, ExtractionJob job) {
		userRepository.findWithLockById(userId).orElseThrow(() -> new BusinessException(ErrorCode.UNAUTHORIZED));
		long usedToday = jobRepository.countByUserIdAndCountsTowardLimitTrueAndCreatedAtGreaterThanEqual(userId,
				ExtractionService.startOfToday(Instant.now(clock)));
		if (usedToday >= ExtractionService.DAILY_LIMIT) {
			throw new BusinessException(ErrorCode.EXTRACTION_DAILY_LIMIT);
		}
		job.countTowardLimit();
	}

	/** ingredientId가 모두 실제 성분인지 확인한다. 하나라도 없으면 INVALID_REQUEST */
	private Map<Long, Ingredient> loadIngredients(List<FinalItem> items) {
		Set<Long> ids = items.stream()
				.map(FinalItem::ingredientId)
				.filter(id -> id != null)
				.collect(Collectors.toCollection(LinkedHashSet::new));
		if (ids.isEmpty()) {
			return Map.of();
		}
		Map<Long, Ingredient> found = ingredientRepository.findAllById(ids).stream()
				.collect(Collectors.toMap(Ingredient::getId, Function.identity()));
		if (found.size() != ids.size()) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST);
		}
		return found;
	}

	/** productId가 있으면 그 제품, 없으면 바코드로 찾고, 그래도 없으면 새로 만든다(바코드 중복이면 제약 위반을 던진다) */
	private Product findOrCreateProduct(ConfirmRequest request, User user) {
		if (request.productId() != null) {
			return productRepository.findById(request.productId())
					.orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
		}
		NewProduct info = request.newProduct();
		String barcode = blankToNull(info.barcode());
		if (barcode != null) {
			Optional<Product> existing = productRepository.findByBarcode(barcode);
			if (existing.isPresent()) {
				return existing.get();
			}
		}
		return productRepository.saveAndFlush(Product.create(barcode, info.brand().strip(), info.name().strip(),
				blankToNull(info.volume()), info.category(), user));
	}

	/** 같은 제품에 같은 fingerprint가 있으면 그 버전(행 잠금), 없으면 PENDING으로 새로 만든다 */
	private IngredientList findOrCreateList(Product product, String fingerprint, List<FinalItem> items,
			Map<Long, Ingredient> ingredients, User user) {
		Optional<Long> existingId = listRepository.findIdByProductIdAndFingerprint(product.getId(), fingerprint);
		if (existingId.isPresent()) {
			return listRepository.findWithLockById(existingId.get()).orElseThrow();
		}
		IngredientList list = listRepository.saveAndFlush(IngredientList.create(product, fingerprint, user));
		Set<String> aliasKeys = aliasKeys(items, ingredients);
		List<IngredientListItem> rows = new ArrayList<>(items.size());
		for (int i = 0; i < items.size(); i++) {
			FinalItem item = items.get(i);
			Ingredient ingredient = item.ingredientId() == null ? null : ingredients.get(item.ingredientId());
			rows.add(IngredientListItem.create(list, i + 1, ingredient, item.rawText().strip(),
					matchType(item, ingredient, aliasKeys)));
		}
		itemRepository.saveAll(rows);
		itemRepository.flush();
		return list;
	}

	/**
	 * 매칭 종류는 화면이 보내지 않으므로 서버가 매처(설계 4.1 5단계)와 같은 기준으로 다시 판정한다.
	 * 성분이 없으면 UNMATCHED, 정규화한 원문이 표준명과 같으면 EXACT, 그 성분의 이명과 같으면 ALIAS,
	 * 그 외(후보에서 고르거나 직접 고른 성분)는 FUZZY.
	 */
	private static MatchType matchType(FinalItem item, Ingredient ingredient, Set<String> aliasKeys) {
		if (ingredient == null) {
			return MatchType.UNMATCHED;
		}
		String normalized = normalize(item.rawText());
		if (normalized.equals(normalize(ingredient.getStandardName()))) {
			return MatchType.EXACT;
		}
		if (aliasKeys.contains(aliasKey(ingredient.getId(), normalized))) {
			return MatchType.ALIAS;
		}
		return MatchType.FUZZY;
	}

	/** (성분 ID, 정규화 이름) 중 실제로 이명으로 등록된 것. 한 번의 조회로 가져온다 */
	private Set<String> aliasKeys(List<FinalItem> items, Map<Long, Ingredient> ingredients) {
		if (ingredients.isEmpty()) {
			return Set.of();
		}
		Set<String> names = items.stream()
				.filter(item -> item.ingredientId() != null)
				.map(item -> normalize(item.rawText()))
				.filter(name -> !name.isEmpty())
				.collect(Collectors.toSet());
		if (names.isEmpty()) {
			return Set.of();
		}
		Set<String> keys = new HashSet<>();
		for (IngredientAlias alias : aliasRepository.findByIngredientIdInAndNormalizedAliasIn(ingredients.keySet(),
				names)) {
			keys.add(aliasKey(alias.getIngredient().getId(), alias.getNormalizedAlias()));
		}
		return keys;
	}

	private static String aliasKey(Long ingredientId, String normalizedName) {
		return ingredientId + "\n" + normalizedName;
	}

	private static String normalize(String name) {
		return IngredientNameNormalizer.normalize(name).value();
	}

	private static String blankToNull(String value) {
		return value == null || value.isBlank() ? null : value.strip();
	}
}
