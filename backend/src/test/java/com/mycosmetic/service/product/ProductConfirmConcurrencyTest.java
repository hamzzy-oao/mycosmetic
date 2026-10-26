package com.mycosmetic.service.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.RepeatedTest;
import org.springframework.beans.factory.annotation.Autowired;

import com.mycosmetic.dto.product.ConfirmRequest;
import com.mycosmetic.dto.product.ConfirmRequest.NewProduct;
import com.mycosmetic.dto.product.ConfirmResponse;
import com.mycosmetic.dto.product.FinalItem;
import com.mycosmetic.entity.Category;
import com.mycosmetic.entity.ExtractionJob;
import com.mycosmetic.entity.Ingredient;
import com.mycosmetic.entity.IngredientList;
import com.mycosmetic.entity.ListStatus;
import com.mycosmetic.entity.Product;
import com.mycosmetic.entity.User;
import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.repository.ExtractionJobRepository;
import com.mycosmetic.repository.IngredientListRepository;
import com.mycosmetic.repository.IngredientRepository;
import com.mycosmetic.repository.ListConfirmationRepository;
import com.mycosmetic.repository.ProductRepository;
import com.mycosmetic.repository.ShelfItemRepository;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.TestAuthHelper;

/**
 * 설계 4.3 동시 저장. @Transactional 없이 두 스레드가 각자 커밋한다.
 * barcode와 (product_id, fingerprint) 중복 불가 제약이 하나만 통과시키고, 진 쪽은 1회 다시 시도해 확인 추가로 끝난다.
 */
class ProductConfirmConcurrencyTest extends IntegrationTestSupport {

	@Autowired
	ProductConfirmService confirmService;

	@Autowired
	TestAuthHelper auth;

	@Autowired
	ExtractionJobRepository jobRepository;

	@Autowired
	IngredientRepository ingredientRepository;

	@Autowired
	ProductRepository productRepository;

	@Autowired
	IngredientListRepository listRepository;

	@Autowired
	ListConfirmationRepository confirmationRepository;

	@Autowired
	ShelfItemRepository shelfItemRepository;

	private ExtractionJob doneJob(User user) {
		ExtractionJob job = ExtractionJob.queue(user);
		job.complete("{\"items\":[]}", Instant.now());
		return jobRepository.save(job);
	}

	@RepeatedTest(10)
	@DisplayName("같은 새 제품을 동시에 확인하면 제품 1, 버전 1, 확인 2, VERIFIED, 두 요청 모두 성공")
	void concurrentConfirmOfSameNewProduct() throws Exception {
		User a = auth.createUser(TestAuthHelper.uniqueEmail());
		User b = auth.createUser(TestAuthHelper.uniqueEmail());
		String unique = UUID.randomUUID().toString().substring(0, 12);
		Ingredient water = ingredientRepository.save(Ingredient.create("정제수-" + unique, null, null));
		String barcode = "880" + unique;
		List<FinalItem> items = List.of(new FinalItem(water.getId(), water.getStandardName()),
				new FinalItem(null, "모르는성분"));
		NewProduct newProduct = new NewProduct(barcode, "브랜드", "토너 " + unique, "200ml", Category.SKINCARE);
		ConfirmRequest requestA = new ConfirmRequest(doneJob(a).getId(), null, newProduct, items);
		ConfirmRequest requestB = new ConfirmRequest(doneJob(b).getId(), null, newProduct, items);

		CountDownLatch ready = new CountDownLatch(2);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(2);
		try {
			Future<ConfirmResponse> fa = pool.submit(() -> {
				ready.countDown();
				start.await();
				return confirmService.confirm(a.getId(), requestA);
			});
			Future<ConfirmResponse> fb = pool.submit(() -> {
				ready.countDown();
				start.await();
				return confirmService.confirm(b.getId(), requestB);
			});
			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			// 둘 다 예외 없이 끝나야 한다(사용자에게 오류를 보여주지 않는다)
			ConfirmResponse ra = fa.get(30, TimeUnit.SECONDS);
			ConfirmResponse rb = fb.get(30, TimeUnit.SECONDS);

			assertThat(ra.productId()).isEqualTo(rb.productId());
			assertThat(ra.ingredientListId()).isEqualTo(rb.ingredientListId());

			Product product = productRepository.findByBarcode(barcode).orElseThrow();
			assertThat(product.getId()).isEqualTo(ra.productId());
			List<IngredientList> lists = listRepository.findByProductId(product.getId());
			assertThat(lists).hasSize(1);
			IngredientList list = lists.get(0);
			assertThat(list.getConfirmCount()).isEqualTo(2);
			assertThat(list.getStatus()).isEqualTo(ListStatus.VERIFIED);
			assertThat(confirmationRepository.countByListId(list.getId())).isEqualTo(2);
			assertThat(shelfItemRepository.existsByUserIdAndIngredientListId(a.getId(), list.getId())).isTrue();
			assertThat(shelfItemRepository.existsByUserIdAndIngredientListId(b.getId(), list.getId())).isTrue();
			assertThat(jobRepository.findById(requestA.jobId()).orElseThrow().isConfirmed()).isTrue();
			assertThat(jobRepository.findById(requestB.jobId()).orElseThrow().isConfirmed()).isTrue();
		} finally {
			pool.shutdownNow();
		}
	}

	@RepeatedTest(5)
	@DisplayName("같은 새 바코드를 4명이 동시에 확인해도 500은 없다: 성공하거나 409 CONFIRM_CONFLICT")
	void fourConcurrentConfirms_noServerError() throws Exception {
		int threads = 4;
		String unique = UUID.randomUUID().toString().substring(0, 12);
		Ingredient water = ingredientRepository.save(Ingredient.create("정제수-" + unique, null, null));
		String barcode = "881" + unique;
		List<FinalItem> items = List.of(new FinalItem(water.getId(), water.getStandardName()));
		NewProduct newProduct = new NewProduct(barcode, "브랜드", "앰플 " + unique, null, Category.SKINCARE);
		List<User> users = new ArrayList<>();
		List<ConfirmRequest> requests = new ArrayList<>();
		for (int i = 0; i < threads; i++) {
			User user = auth.createUser(TestAuthHelper.uniqueEmail());
			users.add(user);
			requests.add(new ConfirmRequest(doneJob(user).getId(), null, newProduct, items));
		}

		CountDownLatch ready = new CountDownLatch(threads);
		CountDownLatch start = new CountDownLatch(1);
		ExecutorService pool = Executors.newFixedThreadPool(threads);
		try {
			List<Future<ConfirmResponse>> futures = new ArrayList<>();
			for (int i = 0; i < threads; i++) {
				User user = users.get(i);
				ConfirmRequest request = requests.get(i);
				futures.add(pool.submit(() -> {
					ready.countDown();
					start.await();
					return confirmService.confirm(user.getId(), request);
				}));
			}
			assertThat(ready.await(10, TimeUnit.SECONDS)).isTrue();
			start.countDown();

			int succeeded = 0;
			for (Future<ConfirmResponse> future : futures) {
				try {
					future.get(60, TimeUnit.SECONDS);
					succeeded++;
				} catch (ExecutionException e) {
					// 500이 될 예외(DataIntegrityViolation, 잠금 실패 등)가 아니라 409 비즈니스 오류여야 한다
					assertThat(e.getCause()).isInstanceOf(BusinessException.class);
					assertThat(((BusinessException) e.getCause()).errorCode()).isEqualTo(ErrorCode.CONFIRM_CONFLICT);
				}
			}
			assertThat(succeeded).isGreaterThanOrEqualTo(1);
			Product product = productRepository.findByBarcode(barcode).orElseThrow();
			assertThat(listRepository.findByProductId(product.getId())).hasSize(1);
		} finally {
			pool.shutdownNow();
		}
	}
}
