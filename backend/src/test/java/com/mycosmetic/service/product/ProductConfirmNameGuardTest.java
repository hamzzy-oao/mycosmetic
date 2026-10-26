package com.mycosmetic.service.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.mycosmetic.dto.product.ConfirmRequest;
import com.mycosmetic.dto.product.ConfirmRequest.NewProduct;
import com.mycosmetic.dto.product.ConfirmResponse;
import com.mycosmetic.dto.product.FinalItem;
import com.mycosmetic.entity.Category;
import com.mycosmetic.entity.ExtractionJob;
import com.mycosmetic.entity.Product;
import com.mycosmetic.entity.ProductSource;
import com.mycosmetic.entity.User;
import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.repository.ExtractionJobRepository;
import com.mycosmetic.repository.ProductRepository;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.ProductFixtures;
import com.mycosmetic.support.TestAuthHelper;

/** 새 제품 저장 전 같은 이름 확인과 출처(2026-10-26 제품명 예외처리). 실제 커밋으로 확인한다 */
class ProductConfirmNameGuardTest extends IntegrationTestSupport {

	@Autowired
	ProductConfirmService confirmService;

	@Autowired
	TestAuthHelper auth;

	@Autowired
	ExtractionJobRepository jobRepository;

	@Autowired
	ProductRepository productRepository;

	@Autowired
	ProductFixtures fixtures;

	private ExtractionJob doneJob(User user) {
		ExtractionJob job = ExtractionJob.queue(user);
		job.complete("{\"ingredientSectionFound\":true,\"unreadableParts\":[],\"warning\":null,\"items\":[]}",
				Instant.now());
		return jobRepository.save(job);
	}

	private ConfirmRequest request(ExtractionJob job, NewProduct product) {
		return new ConfirmRequest(job.getId(), null, product, List.of(new FinalItem(null, "정제수" + ProductFixtures.unique())));
	}

	@Test
	@DisplayName("같은 브랜드에 같은 이름(표기 규칙·공백 무시) 제품이 있으면 PRODUCT_SAME_NAME_EXISTS, 다른 제품이라고 하면 저장한다")
	void sameNameNeedsConfirmation() {
		User user = auth.createUser(TestAuthHelper.uniqueEmail());
		String brand = "중복확인" + ProductFixtures.unique();
		fixtures.product(ProductFixtures.newBarcode(), brand, "윤조에센스");
		long before = productRepository.count();

		ExtractionJob job = doneJob(user);
		NewProduct same = new NewProduct(null, brand, brand + " 윤조 에센스 60ml", null, Category.SKINCARE);
		assertThatThrownBy(() -> confirmService.confirm(user.getId(), request(job, same)))
				.extracting(e -> ((BusinessException) e).errorCode()).isEqualTo(ErrorCode.PRODUCT_SAME_NAME_EXISTS);
		assertThat(productRepository.count()).isEqualTo(before);

		NewProduct different = new NewProduct(null, brand, "윤조에센스 리뉴얼", null, Category.SKINCARE, null, true);
		ConfirmResponse saved = confirmService.confirm(user.getId(), request(job, different));
		Product created = productRepository.findById(saved.productId()).orElseThrow();
		assertThat(created.getSource()).isEqualTo(ProductSource.USER);
		assertThat(created.isVerified()).as("사용자가 직접 입력한 제품은 검수 전").isFalse();
	}

	@Test
	@DisplayName("다른 제품이라고 확인했으면 같은 이름이어도 저장한다")
	void differentProductFlag() {
		User user = auth.createUser(TestAuthHelper.uniqueEmail());
		String brand = "중복확인" + ProductFixtures.unique();
		fixtures.product(ProductFixtures.newBarcode(), brand, "수분크림");

		ConfirmResponse saved = confirmService.confirm(user.getId(), request(doneJob(user),
				new NewProduct(null, brand, "수분크림", null, Category.SKINCARE, null, true)));

		assertThat(saved.productId()).isNotNull();
	}
}
