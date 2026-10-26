package com.mycosmetic.service.user;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.LocalDate;
import java.util.EnumSet;
import java.util.List;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.jdbc.core.JdbcTemplate;

import com.mycosmetic.entity.Category;
import com.mycosmetic.entity.ExtractionJob;
import com.mycosmetic.entity.IngredientList;
import com.mycosmetic.entity.ListConfirmation;
import com.mycosmetic.entity.Product;
import com.mycosmetic.entity.Routine;
import com.mycosmetic.entity.RoutineCheck;
import com.mycosmetic.entity.RoutineSlot;
import com.mycosmetic.entity.ShelfItem;
import com.mycosmetic.entity.SkinProfile;
import com.mycosmetic.entity.SkinType;
import com.mycosmetic.entity.StepType;
import com.mycosmetic.entity.User;
import com.mycosmetic.entity.Verdict;
import com.mycosmetic.repository.ExtractionJobRepository;
import com.mycosmetic.repository.IngredientListRepository;
import com.mycosmetic.repository.ListConfirmationRepository;
import com.mycosmetic.repository.ProductRepository;
import com.mycosmetic.repository.RoutineCheckRepository;
import com.mycosmetic.repository.RoutineRepository;
import com.mycosmetic.repository.ShelfItemRepository;
import com.mycosmetic.repository.SkinProfileRepository;
import com.mycosmetic.service.auth.RefreshTokenService;
import com.mycosmetic.service.auth.SocialAccountService;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.TestAuthHelper;

/** 설계 5.5 탈퇴. 외래 키 동작(SET NULL)까지 확인하려고 실제 MySQL(mycosmetic_test)로 돌린다 */
class UserServiceWithdrawTest extends IntegrationTestSupport {

	@Autowired
	UserService userService;

	@Autowired
	TestAuthHelper auth;

	@Autowired
	JdbcTemplate jdbcTemplate;

	@Autowired
	SocialAccountService socialAccountService;

	@Autowired
	RefreshTokenService refreshTokenService;

	@Autowired
	ProductRepository productRepository;

	@Autowired
	IngredientListRepository ingredientListRepository;

	@Autowired
	ListConfirmationRepository listConfirmationRepository;

	@Autowired
	ShelfItemRepository shelfItemRepository;

	@Autowired
	SkinProfileRepository skinProfileRepository;

	@Autowired
	ExtractionJobRepository extractionJobRepository;

	@Autowired
	RoutineRepository routineRepository;

	@Autowired
	RoutineCheckRepository routineCheckRepository;

	/** 한 사용자가 가진 모든 종류의 데이터. 공유 데이터(제품·버전)는 그 사용자가 만든다 */
	private record Owned(User user, Product product, IngredientList list) {
	}

	private Owned userWithEverything() {
		User user = auth.createUser(TestAuthHelper.uniqueEmail());
		socialAccountService.loginOrRegister("k-" + UUID.randomUUID(), "닉", null, user.getId());
		refreshTokenService.issue(user.getId());
		skinProfileRepository.save(SkinProfile.create(user.getId(), EnumSet.of(SkinType.DRY)));
		extractionJobRepository.save(ExtractionJob.queue(user));
		Product product = productRepository
				.save(Product.create(null, "브랜드", "토너 " + UUID.randomUUID(), "200ml", Category.SKINCARE, user));
		IngredientList list = IngredientList.create(product, UUID.randomUUID().toString().replace("-", ""), user);
		list.addConfirmation();
		list = ingredientListRepository.save(list);
		listConfirmationRepository.save(ListConfirmation.create(list, user));
		ShelfItem item = shelfItemRepository.save(ShelfItem.create(user, product, list, Verdict.GOOD, "메모", null, null));
		Routine routine = Routine.create(user, "아침", RoutineSlot.MORNING, null);
		routine.replaceSteps(List.of(item), List.of(StepType.TONER));
		routineRepository.save(routine);
		routineCheckRepository.save(RoutineCheck.of(user, item, LocalDate.now(), RoutineSlot.MORNING));
		return new Owned(user, product, list);
	}

	/** other가 만든 버전을 user가 확인한다(confirm_count +1) */
	private void confirm(User user, IngredientList list) {
		IngredientList fresh = ingredientListRepository.findById(list.getId()).orElseThrow();
		fresh.addConfirmation();
		ingredientListRepository.save(fresh);
		listConfirmationRepository.save(ListConfirmation.create(fresh, user));
	}

	private int count(String table, Long userId) {
		return jdbcTemplate.queryForObject("select count(*) from " + table + " where user_id = ?", Integer.class,
				userId);
	}

	private int userRows(Long userId) {
		return jdbcTemplate.queryForObject("select count(*) from users where id = ?", Integer.class, userId);
	}

	private void assertAllPrivateRows(Long userId, int expected) {
		assertThat(count("routines", userId)).isEqualTo(expected);
		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from routine_steps s join shelf_items i on i.id = s.shelf_item_id where i.user_id = ?",
				Integer.class, userId)).isEqualTo(expected);
		assertThat(count("routine_checks", userId)).isEqualTo(expected);
		assertThat(count("shelf_items", userId)).isEqualTo(expected);
		assertThat(count("skin_profiles", userId)).isEqualTo(expected);
		assertThat(count("extraction_jobs", userId)).isEqualTo(expected);
		assertThat(count("refresh_tokens", userId)).isEqualTo(expected);
		assertThat(count("social_accounts", userId)).isEqualTo(expected);
		assertThat(count("list_confirmations", userId)).isEqualTo(expected);
		assertThat(userRows(userId)).isEqualTo(expected);
	}

	@Test
	@DisplayName("routines, routine_steps, shelf_items, skin_profiles, extraction_jobs, refresh_tokens, social_accounts, users를 지운다")
	void withdraw_deletesPrivateRows() {
		Owned a = userWithEverything();
		assertAllPrivateRows(a.user().getId(), 1);

		userService.withdraw(a.user().getId());

		assertAllPrivateRows(a.user().getId(), 0);
		assertThat(userService.exists(a.user().getId())).isFalse();
	}

	@Test
	@DisplayName("내가 만든 products·ingredient_lists의 created_by와 내 list_confirmations.user_id는 null, confirm_count는 그대로")
	void withdraw_anonymizesSharedRows() {
		Owned a = userWithEverything();
		Owned b = userWithEverything();
		confirm(a.user(), b.list());   // A가 B의 버전도 확인했다 → confirm_count 2

		userService.withdraw(a.user().getId());

		assertThat(jdbcTemplate.queryForObject("select created_by from products where id = ?", Long.class,
				a.product().getId())).isNull();
		assertThat(jdbcTemplate.queryForObject("select created_by from ingredient_lists where id = ?", Long.class,
				a.list().getId())).isNull();
		// 공유 데이터 자체는 남는다
		assertThat(productRepository.existsById(a.product().getId())).isTrue();
		assertThat(ingredientListRepository.existsById(a.list().getId())).isTrue();
		// A의 확인은 익명으로 남고 횟수는 바뀌지 않는다
		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from list_confirmations where list_id = ? and user_id is null", Integer.class,
				a.list().getId())).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject(
				"select count(*) from list_confirmations where list_id = ? and user_id is null", Integer.class,
				b.list().getId())).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("select confirm_count from ingredient_lists where id = ?",
				Integer.class, a.list().getId())).isEqualTo(1);
		assertThat(jdbcTemplate.queryForObject("select confirm_count from ingredient_lists where id = ?",
				Integer.class, b.list().getId())).isEqualTo(2);
	}

	@Test
	@DisplayName("다른 사용자의 데이터는 그대로")
	void withdraw_keepsOtherUsersData() {
		Owned a = userWithEverything();
		Owned b = userWithEverything();

		userService.withdraw(a.user().getId());

		assertAllPrivateRows(b.user().getId(), 1);
		assertThat(jdbcTemplate.queryForObject("select created_by from products where id = ?", Long.class,
				b.product().getId())).isEqualTo(b.user().getId());
		assertThat(jdbcTemplate.queryForObject("select created_by from ingredient_lists where id = ?", Long.class,
				b.list().getId())).isEqualTo(b.user().getId());
	}
}
