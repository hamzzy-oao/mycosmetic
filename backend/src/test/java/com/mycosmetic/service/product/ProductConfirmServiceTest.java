package com.mycosmetic.service.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.Arrays;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.mycosmetic.dto.product.ConfirmRequest;
import com.mycosmetic.dto.product.ConfirmRequest.NewProduct;
import com.mycosmetic.dto.product.ConfirmResponse;
import com.mycosmetic.dto.product.FinalItem;
import com.mycosmetic.entity.Category;
import com.mycosmetic.entity.ExtractionJob;
import com.mycosmetic.entity.Ingredient;
import com.mycosmetic.entity.IngredientAlias;
import com.mycosmetic.entity.IngredientList;
import com.mycosmetic.entity.IngredientListItem;
import com.mycosmetic.entity.ListStatus;
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
import com.mycosmetic.service.ingredient.IngredientNameNormalizer;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.TestAuthHelper;

/** 설계 4.1 9단계(확인·저장)와 3.5(버전 규칙). 트랜잭션 없이 실제 커밋으로 확인한다 */
class ProductConfirmServiceTest extends IntegrationTestSupport {

	@Autowired
	ProductConfirmService confirmService;

	@Autowired
	TestAuthHelper auth;

	@Autowired
	ExtractionJobRepository jobRepository;

	@Autowired
	ProductRepository productRepository;

	@Autowired
	IngredientRepository ingredientRepository;

	@Autowired
	IngredientAliasRepository aliasRepository;

	@Autowired
	IngredientListRepository listRepository;

	@Autowired
	IngredientListItemRepository itemRepository;

	@Autowired
	ListConfirmationRepository confirmationRepository;

	@Autowired
	ShelfItemRepository shelfItemRepository;

	// ---- 도우미 ----

	private User newUser() {
		return auth.createUser(TestAuthHelper.uniqueEmail());
	}

	static String unique() {
		return UUID.randomUUID().toString().substring(0, 12);
	}

	static String newBarcode() {
		return "880" + unique();
	}

	private ExtractionJob doneJob(User user) {
		ExtractionJob job = ExtractionJob.queue(user);
		job.complete("{\"ingredientSectionFound\":true,\"unreadableParts\":[],\"warning\":null,\"items\":[]}",
				Instant.now());
		return jobRepository.save(job);
	}

	private ExtractionJob failedJob(User user) {
		ExtractionJob job = ExtractionJob.queue(user);
		job.fail("AI_TIMEOUT", false, Instant.now());
		return jobRepository.save(job);
	}

	/** 표준명과 같은 alias를 함께 넣은 성분 */
	private Ingredient ingredient(String standardName) {
		Ingredient ingredient = ingredientRepository.save(Ingredient.create(standardName, null, null));
		aliasRepository.save(IngredientAlias.create(ingredient, standardName,
				IngredientNameNormalizer.normalize(standardName).value()));
		return ingredient;
	}

	private static NewProduct newProduct(String barcode) {
		return new NewProduct(barcode, "브랜드", "토너 " + unique(), "200ml", Category.SKINCARE);
	}

	private static List<FinalItem> items(Ingredient... ingredients) {
		return Arrays.stream(ingredients)
				.map(i -> new FinalItem(i.getId(), i.getStandardName()))
				.toList();
	}

	private IngredientList list(Long id) {
		return listRepository.findById(id).orElseThrow();
	}

	private ExtractionJob job(Long id) {
		return jobRepository.findById(id).orElseThrow();
	}

	private static ErrorCode codeOf(Throwable e) {
		return ((BusinessException) e).errorCode();
	}

	// ---- 테스트 ----

	@Test
	@DisplayName("새 제품이면 제품·PENDING 버전·확인 1·화장대 항목을 만들고 job 결과를 지운다")
	void newProduct_createsEverything() {
		User user = newUser();
		ExtractionJob job = doneJob(user);
		Ingredient water = ingredient("정제수-" + unique());
		Ingredient glycerin = ingredient("글리세린-" + unique());
		String barcode = newBarcode();

		ConfirmResponse res = confirmService.confirm(user.getId(),
				new ConfirmRequest(job.getId(), null, newProduct(barcode),
						List.of(new FinalItem(water.getId(), water.getStandardName()),
								new FinalItem(glycerin.getId(), glycerin.getStandardName()),
								new FinalItem(null, "알수없는성분"))));

		Product product = productRepository.findById(res.productId()).orElseThrow();
		assertThat(product.getBarcode()).isEqualTo(barcode);
		assertThat(product.getVolume()).isEqualTo("200ml");

		IngredientList list = list(res.ingredientListId());
		assertThat(list.getProduct().getId()).isEqualTo(product.getId());
		assertThat(list.getStatus()).isEqualTo(ListStatus.PENDING);
		assertThat(list.getConfirmCount()).isEqualTo(1);
		assertThat(res.status()).isEqualTo(ListStatus.PENDING);
		assertThat(confirmationRepository.existsByListIdAndUserId(list.getId(), user.getId())).isTrue();

		List<IngredientListItem> saved = itemRepository.findByListIdOrderByPosition(list.getId());
		assertThat(saved).extracting(IngredientListItem::getPosition).containsExactly(1, 2, 3);
		assertThat(saved).extracting(IngredientListItem::getRawText)
				.containsExactly(water.getStandardName(), glycerin.getStandardName(), "알수없는성분");
		assertThat(saved).extracting(IngredientListItem::getMatchType)
				.containsExactly(MatchType.EXACT, MatchType.EXACT, MatchType.UNMATCHED);

		ShelfItem shelf = shelfItemRepository.findByIdAndUserId(res.shelfItemId(), user.getId()).orElseThrow();
		assertThat(shelf.getIngredientList().getId()).isEqualTo(list.getId());
		assertThat(shelf.getProduct().getId()).isEqualTo(product.getId());
		assertThat(shelf.getVerdict()).isEqualTo(Verdict.UNRATED);

		ExtractionJob after = job(job.getId());
		assertThat(after.getResultJson()).isNull();
		assertThat(after.isConfirmed()).isTrue();
	}

	@Test
	@DisplayName("다른 사용자가 같은 성분으로 확인하면 같은 버전에 확인이 추가되어 VERIFIED")
	void secondUser_sameItems_verifies() {
		User a = newUser();
		User b = newUser();
		Ingredient water = ingredient("정제수-" + unique());
		ConfirmResponse first = confirmService.confirm(a.getId(),
				new ConfirmRequest(doneJob(a).getId(), null, newProduct(newBarcode()), items(water)));

		ConfirmResponse second = confirmService.confirm(b.getId(),
				new ConfirmRequest(doneJob(b).getId(), first.productId(), null, items(water)));

		assertThat(second.ingredientListId()).isEqualTo(first.ingredientListId());
		assertThat(second.status()).isEqualTo(ListStatus.VERIFIED);
		IngredientList list = list(first.ingredientListId());
		assertThat(list.getConfirmCount()).isEqualTo(2);
		assertThat(list.getStatus()).isEqualTo(ListStatus.VERIFIED);
		assertThat(confirmationRepository.countByListId(list.getId())).isEqualTo(2);
		assertThat(second.shelfItemId()).isNotEqualTo(first.shelfItemId());
	}

	@Test
	@DisplayName("개봉 후 사용기간을 보내면 새 화장대 항목에 넣고 개봉일은 비워 둔다")
	void paoMonths_onNewShelfItem() {
		User user = newUser();
		Ingredient water = ingredient("정제수-" + unique());

		ConfirmResponse res = confirmService.confirm(user.getId(),
				new ConfirmRequest(doneJob(user).getId(), null, newProduct(newBarcode()), items(water), 12));

		ShelfItem shelf = shelfItemRepository.findByIdAndUserId(res.shelfItemId(), user.getId()).orElseThrow();
		assertThat(shelf.getPaoMonths()).isEqualTo(12);
		assertThat(shelf.getOpenedOn()).isNull();
	}

	@Test
	@DisplayName("같은 사용자가 두 번 확인하면 confirm_count는 그대로, 화장대 항목도 하나")
	void sameUserTwice_countsOnce() {
		User user = newUser();
		Ingredient water = ingredient("정제수-" + unique());
		ConfirmResponse first = confirmService.confirm(user.getId(),
				new ConfirmRequest(doneJob(user).getId(), null, newProduct(newBarcode()), items(water)));

		ConfirmResponse second = confirmService.confirm(user.getId(),
				new ConfirmRequest(doneJob(user).getId(), first.productId(), null, items(water)));

		assertThat(second.ingredientListId()).isEqualTo(first.ingredientListId());
		assertThat(second.shelfItemId()).isEqualTo(first.shelfItemId());
		assertThat(second.status()).isEqualTo(ListStatus.PENDING);
		assertThat(list(first.ingredientListId()).getConfirmCount()).isEqualTo(1);
		assertThat(confirmationRepository.countByListId(first.ingredientListId())).isEqualTo(1);
		assertThat(shelfItemRepository.findByUserIdOrderByUpdatedAtDesc(user.getId())).hasSize(1);
	}

	@Test
	@DisplayName("성분이 다르면 같은 제품의 새 버전, 기존 버전은 그대로")
	void differentItems_newVersion() {
		User a = newUser();
		User b = newUser();
		Ingredient water = ingredient("정제수-" + unique());
		Ingredient glycerin = ingredient("글리세린-" + unique());
		ConfirmResponse first = confirmService.confirm(a.getId(),
				new ConfirmRequest(doneJob(a).getId(), null, newProduct(newBarcode()), items(water, glycerin)));

		// 순서만 바뀌어도 다른 버전이다
		ConfirmResponse second = confirmService.confirm(b.getId(),
				new ConfirmRequest(doneJob(b).getId(), first.productId(), null, items(glycerin, water)));

		assertThat(second.productId()).isEqualTo(first.productId());
		assertThat(second.ingredientListId()).isNotEqualTo(first.ingredientListId());
		assertThat(second.status()).isEqualTo(ListStatus.PENDING);
		assertThat(listRepository.findByProductId(first.productId())).hasSize(2);
		IngredientList old = list(first.ingredientListId());
		assertThat(old.getConfirmCount()).isEqualTo(1);
		assertThat(old.getStatus()).isEqualTo(ListStatus.PENDING);
		assertThat(itemRepository.findByListIdOrderByPosition(old.getId()))
				.extracting(item -> item.getIngredient().getId())
				.containsExactly(water.getId(), glycerin.getId());
	}

	@Test
	@DisplayName("기존 바코드로 newProduct를 보내면 그 제품을 쓴다")
	void existingBarcode_reusesProduct() {
		User a = newUser();
		User b = newUser();
		Ingredient water = ingredient("정제수-" + unique());
		String barcode = newBarcode();
		ConfirmResponse first = confirmService.confirm(a.getId(),
				new ConfirmRequest(doneJob(a).getId(), null, newProduct(barcode), items(water)));

		ConfirmResponse second = confirmService.confirm(b.getId(),
				new ConfirmRequest(doneJob(b).getId(), null,
						new NewProduct(barcode, "다른 브랜드", "다른 이름", null, Category.MAKEUP), items(water)));

		assertThat(second.productId()).isEqualTo(first.productId());
		assertThat(second.ingredientListId()).isEqualTo(first.ingredientListId());
		Product product = productRepository.findByBarcode(barcode).orElseThrow();
		assertThat(product.getBrand()).isEqualTo("브랜드");
	}

	@Test
	@DisplayName("다른 사람의 jobId면 NOT_FOUND, DONE이 아니면 EXTRACTION_NOT_READY")
	void otherUsersJob_or_notReady() {
		User owner = newUser();
		User other = newUser();
		ExtractionJob done = doneJob(owner);
		Ingredient water = ingredient("정제수-" + unique());

		assertThatThrownBy(() -> confirmService.confirm(other.getId(),
				new ConfirmRequest(done.getId(), null, newProduct(newBarcode()), items(water))))
				.isInstanceOf(BusinessException.class)
				.extracting(ProductConfirmServiceTest::codeOf).isEqualTo(ErrorCode.NOT_FOUND);
		assertThat(job(done.getId()).getResultJson()).isNotNull();
		assertThat(job(done.getId()).isConfirmed()).isFalse();

		ExtractionJob queued = jobRepository.save(ExtractionJob.queue(owner));
		assertThatThrownBy(() -> confirmService.confirm(owner.getId(),
				new ConfirmRequest(queued.getId(), null, newProduct(newBarcode()), items(water))))
				.isInstanceOf(BusinessException.class)
				.extracting(ProductConfirmServiceTest::codeOf).isEqualTo(ErrorCode.EXTRACTION_NOT_READY);

		ExtractionJob running = ExtractionJob.queue(owner);
		running.start(Instant.now());
		Long runningId = jobRepository.save(running).getId();
		assertThatThrownBy(() -> confirmService.confirm(owner.getId(),
				new ConfirmRequest(runningId, null, newProduct(newBarcode()), items(water))))
				.isInstanceOf(BusinessException.class)
				.extracting(ProductConfirmServiceTest::codeOf).isEqualTo(ErrorCode.EXTRACTION_NOT_READY);
	}

	@Test
	@DisplayName("FAILED job(직접 붙여넣기)도 저장할 수 있고 바코드·용량이 없으면 null로 둔다")
	void failedJob_pasteMode_allowed() {
		User user = newUser();
		ExtractionJob failed = failedJob(user);

		ConfirmResponse res = confirmService.confirm(user.getId(), new ConfirmRequest(failed.getId(), null,
				new NewProduct(" ", "브랜드", "크림 " + unique(), "", Category.SKINCARE),
				List.of(new FinalItem(null, "붙여넣은 성분"))));

		Product product = productRepository.findById(res.productId()).orElseThrow();
		assertThat(product.getBarcode()).isNull();
		assertThat(product.getVolume()).isNull();
		assertThat(job(failed.getId()).isConfirmed()).isTrue();
	}

	@Test
	@DisplayName("이미 저장한 job으로 다시 보내면 EXTRACTION_ALREADY_CONFIRMED (FAILED job도)")
	void alreadyConfirmed_rejected() {
		User user = newUser();
		Ingredient water = ingredient("정제수-" + unique());
		ExtractionJob done = doneJob(user);
		ConfirmResponse first = confirmService.confirm(user.getId(),
				new ConfirmRequest(done.getId(), null, newProduct(newBarcode()), items(water)));

		assertThatThrownBy(() -> confirmService.confirm(user.getId(),
				new ConfirmRequest(done.getId(), first.productId(), null, items(water))))
				.isInstanceOf(BusinessException.class)
				.extracting(ProductConfirmServiceTest::codeOf).isEqualTo(ErrorCode.EXTRACTION_ALREADY_CONFIRMED);

		ExtractionJob failed = failedJob(user);
		confirmService.confirm(user.getId(), new ConfirmRequest(failed.getId(), first.productId(), null,
				List.of(new FinalItem(null, "a"))));
		assertThatThrownBy(() -> confirmService.confirm(user.getId(), new ConfirmRequest(failed.getId(),
				first.productId(), null, List.of(new FinalItem(null, "b")))))
				.isInstanceOf(BusinessException.class)
				.extracting(ProductConfirmServiceTest::codeOf).isEqualTo(ErrorCode.EXTRACTION_ALREADY_CONFIRMED);
		assertThat(listRepository.findByProductId(first.productId())).hasSize(2);
	}

	@Test
	@DisplayName("없는 ingredientId, productId·newProduct 둘 다 또는 둘 다 없음은 INVALID_REQUEST, 없는 productId는 NOT_FOUND")
	void invalidRequests() {
		User user = newUser();
		ExtractionJob done = doneJob(user);
		Ingredient water = ingredient("정제수-" + unique());

		assertThatThrownBy(() -> confirmService.confirm(user.getId(), new ConfirmRequest(done.getId(), null,
				newProduct(newBarcode()), List.of(new FinalItem(Long.MAX_VALUE, "가짜")))))
				.extracting(ProductConfirmServiceTest::codeOf).isEqualTo(ErrorCode.INVALID_REQUEST);
		assertThatThrownBy(() -> confirmService.confirm(user.getId(), new ConfirmRequest(done.getId(), 1L,
				newProduct(newBarcode()), items(water))))
				.extracting(ProductConfirmServiceTest::codeOf).isEqualTo(ErrorCode.INVALID_REQUEST);
		assertThatThrownBy(() -> confirmService.confirm(user.getId(),
				new ConfirmRequest(done.getId(), null, null, items(water))))
				.extracting(ProductConfirmServiceTest::codeOf).isEqualTo(ErrorCode.INVALID_REQUEST);
		assertThatThrownBy(() -> confirmService.confirm(user.getId(),
				new ConfirmRequest(done.getId(), Long.MAX_VALUE, null, items(water))))
				.extracting(ProductConfirmServiceTest::codeOf).isEqualTo(ErrorCode.NOT_FOUND);

		// 실패한 요청은 아무것도 남기지 않는다
		ExtractionJob after = job(done.getId());
		assertThat(after.isConfirmed()).isFalse();
		assertThat(after.getResultJson()).isNotNull();
		assertThat(shelfItemRepository.findByUserIdOrderByUpdatedAtDesc(user.getId())).isEmpty();
	}

	@Test
	@DisplayName("매칭 종류는 서버가 판정한다: 표준명 EXACT, 이명 ALIAS, 그 외 고른 성분 FUZZY, 성분 없음 UNMATCHED")
	void matchTypeIsDerivedOnServer() {
		User user = newUser();
		Ingredient niacinamide = ingredient("나이아신아마이드-" + unique());
		String aliasName = "니아신아미드-" + unique();
		aliasRepository.save(IngredientAlias.create(niacinamide, aliasName,
				IngredientNameNormalizer.normalize(aliasName).value()));

		ConfirmResponse res = confirmService.confirm(user.getId(), new ConfirmRequest(doneJob(user).getId(), null,
				newProduct(newBarcode()),
				List.of(new FinalItem(niacinamide.getId(), "  " + niacinamide.getStandardName() + " "),
						new FinalItem(niacinamide.getId(), aliasName),
						new FinalItem(niacinamide.getId(), "나이아신아마이"),
						new FinalItem(null, "모르는성분"))));

		assertThat(itemRepository.findByListIdOrderByPosition(res.ingredientListId()))
				.extracting(IngredientListItem::getMatchType)
				.containsExactly(MatchType.EXACT, MatchType.ALIAS, MatchType.FUZZY, MatchType.UNMATCHED);
	}
}
