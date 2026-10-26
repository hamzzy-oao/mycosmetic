package com.mycosmetic.service.product.review;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.util.ReflectionTestUtils;

import com.mycosmetic.dto.review.ReviewIssueResponse;
import com.mycosmetic.dto.review.ReviewIssueType;
import com.mycosmetic.dto.review.ReviewMember;
import com.mycosmetic.dto.review.ReviewResponse;
import com.mycosmetic.entity.Category;
import com.mycosmetic.entity.Ingredient;
import com.mycosmetic.entity.IngredientAlias;
import com.mycosmetic.entity.IngredientList;
import com.mycosmetic.entity.IngredientListItem;
import com.mycosmetic.entity.MatchType;
import com.mycosmetic.entity.Product;
import com.mycosmetic.entity.User;
import com.mycosmetic.entity.UserRole;
import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.repository.IngredientAliasRepository;
import com.mycosmetic.repository.IngredientListItemRepository;
import com.mycosmetic.repository.ProductRepository;
import com.mycosmetic.repository.UserRepository;
import com.mycosmetic.service.ingredient.IngredientNameNormalizer;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.ProductFixtures;
import com.mycosmetic.support.TestAuthHelper;

/** 관리자 점검함. 테스트끼리 DB를 같이 쓰므로 내가 만든 제품의 문제만 본다 */
class ProductReviewServiceTest extends IntegrationTestSupport {

	@Autowired
	ProductReviewService reviewService;

	@Autowired
	ProductFixtures fixtures;

	@Autowired
	TestAuthHelper auth;

	@Autowired
	UserRepository userRepository;

	@Autowired
	ProductRepository productRepository;

	@Autowired
	IngredientAliasRepository aliasRepository;

	@Autowired
	IngredientListItemRepository itemRepository;

	private User admin() {
		User user = auth.createUser(TestAuthHelper.uniqueEmail());
		ReflectionTestUtils.setField(user, "role", UserRole.ADMIN);
		return userRepository.save(user);
	}

	private static List<ReviewIssueResponse> of(ReviewResponse r, Product p, ReviewIssueType type) {
		return r.issues().stream().filter(i -> i.productId().equals(p.getId()) && i.type() == type).toList();
	}

	private Ingredient dictionary(String standardName) {
		Ingredient ingredient = fixtures.ingredientNamed(standardName);
		aliasRepository.save(IngredientAlias.create(ingredient, standardName,
				IngredientNameNormalizer.normalize(standardName).value()));
		return ingredient;
	}

	@Test
	@DisplayName("제품명 표기, 브랜드 표기 차이, 중복 의심을 찾는다")
	void nameBrandDuplicate() {
		User admin = admin();
		String u = ProductFixtures.unique();
		String brand = "점검브랜드" + u;
		Product withBrand = fixtures.product(ProductFixtures.newBarcode(), brand, brand + " 수분크림 50ml");
		Product clean = fixtures.product(ProductFixtures.newBarcode(), brand, "수분크림");
		Product variant = fixtures.product(ProductFixtures.newBarcode(), "점검 브랜드" + u, "진정토너");

		ReviewResponse r = reviewService.review(admin.getId());

		ReviewIssueResponse name = of(r, withBrand, ReviewIssueType.NAME_FORMAT).get(0);
		assertThat(name.suggestion().name()).isEqualTo("수분크림");
		assertThat(name.suggestion().volume()).as("용량 칸이 이미 있으면 바꾸자고 하지 않는다").isNull();
		assertThat(name.reasons()).hasSize(2);
		assertThat(of(r, clean, ReviewIssueType.NAME_FORMAT)).isEmpty();

		assertThat(of(r, variant, ReviewIssueType.BRAND_VARIANT).get(0).suggestion().brand()).isEqualTo(brand);
		assertThat(of(r, withBrand, ReviewIssueType.BRAND_VARIANT)).isEmpty();

		// 중복 의심은 묶음 하나에 하나, 먼저 등록된 제품에 붙고 두 제품을 등록 순으로 나란히 보여 준다
		ReviewIssueResponse dup = of(r, withBrand, ReviewIssueType.DUPLICATE_SUSPECT).get(0);
		assertThat(dup.members()).extracting(ReviewMember::productId).containsExactly(withBrand.getId(), clean.getId());
		assertThat(dup.members()).extracting(ReviewMember::shelfCount).containsExactly(0L, 0L);
		assertThat(dup.signature()).isEqualTo(withBrand.getId() + "," + clean.getId());
		assertThat(of(r, clean, ReviewIssueType.DUPLICATE_SUSPECT)).isEmpty();
		assertThat(name.members()).as("다른 종류에는 묶음이 없다").isNull();
		assertThat(r.counts().get(ReviewIssueType.NAME_FORMAT)).isPositive();
	}

	@Test
	@DisplayName("띄어쓰기로 고칠 수 있는 줄과 성분이 아닌 글을 찾고, 띄어쓰기 고치기 뒤에는 그 줄이 성분에 이어진다")
	void ingredientsAndSpacingFix() {
		User admin = admin();
		String u = ProductFixtures.unique();
		Ingredient glycerin = dictionary("글리세린" + u);
		Ingredient water = dictionary("정제수" + u);
		Product product = fixtures.product();
		IngredientList list = fixtures.list(product, 1, water, "글리세 린" + u, "마른 얼굴 위에 30초 동안 도포한 후",
				"모르는성분" + u);

		ReviewResponse before = reviewService.review(admin.getId());

		assertThat(of(before, product, ReviewIssueType.INGREDIENT_SPACING).get(0).examples())
				.containsExactly("글리세 린" + u + " → 글리세린" + u);
		ReviewIssueResponse notList = of(before, product, ReviewIssueType.INGREDIENT_NOT_LIST).get(0);
		assertThat(notList.reasons()).as("문장 1줄 + 4줄뿐").hasSize(2);
		assertThat(before.spacingLines()).isPositive();

		reviewService.fixSpacing(admin.getId());

		List<IngredientListItem> lines = itemRepository.findWithIngredientByListId(list.getId());
		assertThat(lines.get(1).getRawText()).as("원문은 그대로").isEqualTo("글리세 린" + u);
		assertThat(lines.get(1).getMatchType()).isEqualTo(MatchType.EXACT);
		assertThat(lines.get(1).getIngredient().getId()).isEqualTo(glycerin.getId());
		assertThat(lines.get(2).getMatchType()).isEqualTo(MatchType.UNMATCHED);
		assertThat(of(reviewService.review(admin.getId()), product, ReviewIssueType.INGREDIENT_SPACING)).isEmpty();
	}

	@Test
	@DisplayName("괜찮음으로 넘긴 문제는 숨기고, 값이 바뀌면 다시 보여준다")
	void dismissUntilChanged() {
		User admin = admin();
		String brand = "넘김브랜드" + ProductFixtures.unique();
		Product product = fixtures.product(ProductFixtures.newBarcode(), brand, "[단독] 앰플");
		ReviewIssueResponse issue = of(reviewService.review(admin.getId()), product, ReviewIssueType.NAME_FORMAT).get(0);

		reviewService.dismiss(admin.getId(), product.getId(), ReviewIssueType.NAME_FORMAT, issue.signature());
		ReviewResponse hidden = reviewService.review(admin.getId());
		assertThat(of(hidden, product, ReviewIssueType.NAME_FORMAT)).isEmpty();
		assertThat(hidden.dismissed()).isPositive();

		Product reloaded = productRepository.findById(product.getId()).orElseThrow();
		reloaded.edit(brand, "[기획] 앰플", reloaded.getVolume(), Category.SKINCARE);
		productRepository.saveAndFlush(reloaded);
		assertThat(of(reviewService.review(admin.getId()), product, ReviewIssueType.NAME_FORMAT)).hasSize(1);
	}

	@Test
	@DisplayName("중복 의심: 하나를 보관하면 사라지고, 둘 다 두기로 숨긴 뒤 같은 이름 제품이 또 생기면 다시 뜬다")
	void duplicateKeepOrArchive() {
		User admin = admin();
		String brand = "중복브랜드" + ProductFixtures.unique();
		Product a = fixtures.product(ProductFixtures.newBarcode(), brand, "진설크림");
		Product b = fixtures.product(ProductFixtures.newBarcode(), brand, "진설크림 리필");
		ReviewIssueResponse dup = of(reviewService.review(admin.getId()), a, ReviewIssueType.DUPLICATE_SUSPECT).get(0);

		reviewService.dismiss(admin.getId(), a.getId(), ReviewIssueType.DUPLICATE_SUSPECT, dup.signature());
		assertThat(of(reviewService.review(admin.getId()), a, ReviewIssueType.DUPLICATE_SUSPECT)).isEmpty();

		Product c = fixtures.product(ProductFixtures.newBarcode(), brand, brand + " 진설 크림 60ml");
		ReviewIssueResponse again = of(reviewService.review(admin.getId()), a, ReviewIssueType.DUPLICATE_SUSPECT).get(0);
		assertThat(again.members()).extracting(ReviewMember::productId).containsExactly(a.getId(), b.getId(), c.getId());

		for (Product p : List.of(b, c)) {
			Product reloaded = productRepository.findById(p.getId()).orElseThrow();
			reloaded.archive();
			productRepository.saveAndFlush(reloaded);
		}
		assertThat(of(reviewService.review(admin.getId()), a, ReviewIssueType.DUPLICATE_SUSPECT)).isEmpty();
	}

	@Test
	@DisplayName("사용자가 직접 입력한 제품은 새로 등록된 제품으로 뜨고, 확인 완료하면 사라진다. 시드 제품은 뜨지 않는다")
	void newUserProducts() {
		User admin = admin();
		User user = auth.createUser(TestAuthHelper.uniqueEmail());
		Product mine = productRepository.save(Product.create(null, "사용자브랜드" + ProductFixtures.unique(), "크림", null,
				Category.SKINCARE, user));
		Product seeded = fixtures.product();

		ReviewIssueResponse issue = of(reviewService.review(admin.getId()), mine, ReviewIssueType.NEW_USER_PRODUCT).get(0);
		assertThat(issue.reasons().get(0)).contains("사용자가 직접 입력함");
		assertThat(of(reviewService.review(admin.getId()), seeded, ReviewIssueType.NEW_USER_PRODUCT)).isEmpty();

		reviewService.verify(admin.getId(), mine.getId());

		assertThat(productRepository.findById(mine.getId()).orElseThrow().isVerified()).isTrue();
		assertThat(of(reviewService.review(admin.getId()), mine, ReviewIssueType.NEW_USER_PRODUCT)).isEmpty();
		assertThatThrownBy(() -> reviewService.verify(user.getId(), mine.getId()))
				.extracting(e -> ((BusinessException) e).errorCode()).isEqualTo(ErrorCode.FORBIDDEN);
	}

	@Test
	@DisplayName("보관한 제품은 점검하지 않고 넘길 수도 없다")
	void archivedProducts() {
		User admin = admin();
		String brand = "보관브랜드" + ProductFixtures.unique();
		Product product = fixtures.product(ProductFixtures.newBarcode(), brand, brand + " 크림");
		product.archive();
		productRepository.saveAndFlush(product);

		assertThat(reviewService.review(admin.getId()).issues()).noneMatch(i -> i.productId().equals(product.getId()));
		assertThatThrownBy(() -> reviewService.dismiss(admin.getId(), product.getId(), ReviewIssueType.NAME_FORMAT, "x"))
				.extracting(e -> ((BusinessException) e).errorCode()).isEqualTo(ErrorCode.NOT_FOUND);
	}

	@Test
	@DisplayName("관리자가 아니면 FORBIDDEN")
	void ordinaryUserForbidden() {
		User user = auth.createUser(TestAuthHelper.uniqueEmail());
		Product product = fixtures.product();

		for (Runnable call : List.<Runnable>of(() -> reviewService.review(user.getId()),
				() -> reviewService.dismiss(user.getId(), product.getId(), ReviewIssueType.NAME_FORMAT, "x"),
				() -> reviewService.fixSpacing(user.getId()))) {
			assertThatThrownBy(call::run).extracting(e -> ((BusinessException) e).errorCode())
					.isEqualTo(ErrorCode.FORBIDDEN);
		}
	}
}
