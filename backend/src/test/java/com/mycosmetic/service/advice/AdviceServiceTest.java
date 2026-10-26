package com.mycosmetic.service.advice;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.time.Instant;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.mycosmetic.dto.advice.AdviceReasonResponse;
import com.mycosmetic.dto.advice.AdviceRequest;
import com.mycosmetic.dto.advice.AdviceResponse;
import com.mycosmetic.dto.advice.RelatedShelfItem;
import com.mycosmetic.dto.routine.RoutineRequest;
import com.mycosmetic.dto.routine.StepRequest;
import com.mycosmetic.dto.shelf.UpdateShelfRequest;
import com.mycosmetic.entity.Ingredient;
import com.mycosmetic.entity.IngredientList;
import com.mycosmetic.entity.Product;
import com.mycosmetic.entity.RoutineSlot;
import com.mycosmetic.entity.SkinType;
import com.mycosmetic.entity.StepType;
import com.mycosmetic.entity.User;
import com.mycosmetic.entity.Verdict;
import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.service.routine.RoutineService;
import com.mycosmetic.service.shelf.ShelfService;
import com.mycosmetic.service.skin.SkinProfileService;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.ProductFixtures;
import com.mycosmetic.support.TestAuthHelper;

/** "이거 사도 돼?" 서비스(no-ai-features 설계 F) — 입력 세 가지와 오류, 실제 DB로 */
class AdviceServiceTest extends IntegrationTestSupport {

	@Autowired
	AdviceService adviceService;

	@Autowired
	ShelfService shelfService;

	@Autowired
	SkinProfileService skinProfileService;

	@Autowired
	RoutineService routineService;

	@Autowired
	TestAuthHelper auth;

	@Autowired
	ProductFixtures fixtures;

	private User newUser() {
		return auth.createUser(TestAuthHelper.uniqueEmail());
	}

	private Long shelfItem(User user, Verdict verdict, Object... lines) {
		Long id = shelfService.add(user.getId(), fixtures.list(fixtures.product(), 1, lines).getId()).id();
		if (verdict != Verdict.UNRATED) {
			shelfService.update(user.getId(), id, new UpdateShelfRequest(verdict, null, null, null));
		}
		return id;
	}

	private static ErrorCode codeOf(Throwable e) {
		return ((BusinessException) e).errorCode();
	}

	private static AdviceReasonResponse reason(AdviceResponse response, ReasonType type) {
		return response.reasons().stream().filter(r -> r.type() == type).findFirst().orElseThrow();
	}

	private static AdviceRequest product(Long id) {
		return new AdviceRequest(id, null);
	}

	private static AdviceRequest text(String text) {
		return new AdviceRequest(null, text);
	}

	@Test
	@DisplayName("제품: 대표 버전 성분으로 판단하고 안 맞았던 성분 2개면 AVOID, 관련 내 제품과 제품 요약을 준다")
	void product_avoid() {
		User user = newUser();
		String x = "자극성분X" + ProductFixtures.unique();
		Ingredient y = fixtures.ingredient("자극성분Y");
		Long bad1 = shelfItem(user, Verdict.BAD, "정제수", x, y);
		Long bad2 = shelfItem(user, Verdict.BAD, x, y, "글리세린");
		shelfItem(user, Verdict.GOOD, "정제수", "판테놀");
		Product target = fixtures.product();
		fixtures.list(target, 1, "정제수");
		IngredientList rep = fixtures.list(target, 2, "정제수", x, y, "판테놀");

		AdviceResponse response = adviceService.advise(user.getId(), product(target.getId()));

		assertThat(response.verdict()).isEqualTo(AdviceVerdict.AVOID);
		AdviceReasonResponse bad = reason(response, ReasonType.BAD_INGREDIENT);
		assertThat(bad.level()).isEqualTo(AdviceLevel.WARNING);
		assertThat(bad.ingredients()).containsExactly(x, y.getStandardName());
		assertThat(bad.relatedShelfItemIds()).containsExactlyInAnyOrder(bad1, bad2);
		assertThat(response.relatedItems()).extracting(RelatedShelfItem::id).contains(bad1, bad2);
		assertThat(response.relatedItems()).filteredOn(i -> i.id().equals(bad1)).singleElement()
				.satisfies(i -> assertThat(i.verdict()).isEqualTo(Verdict.BAD));
		assertThat(response.ingredients()).containsExactly("정제수", x, y.getStandardName(), "판테놀");
		assertThat(response.matchedCount()).isEqualTo(1);
		assertThat(response.unmatchedCount()).isEqualTo(3);
		assertThat(response.product().id()).isEqualTo(target.getId());
		assertThat(response.product().representativeListId()).isEqualTo(rep.getId());
		assertThat(response.alreadyOnShelf()).isFalse();
	}

	@Test
	@DisplayName("제품이 이미 내 화장대에 있으면 alreadyOnShelf와 ALREADY_ON_SHELF 근거")
	void product_alreadyOnShelf() {
		User user = newUser();
		Product target = fixtures.product();
		IngredientList rep = fixtures.list(target, 1, "정제수", "판테놀");
		Long mine = shelfService.add(user.getId(), rep.getId()).id();

		AdviceResponse response = adviceService.advise(user.getId(), product(target.getId()));

		assertThat(response.alreadyOnShelf()).isTrue();
		assertThat(reason(response, ReasonType.ALREADY_ON_SHELF).relatedShelfItemIds()).containsExactly(mine);
		assertThat(response.verdict()).isEqualTo(AdviceVerdict.UNKNOWN);
	}

	@Test
	@DisplayName("없는 제품, 성분 버전이 없는 제품은 NOT_FOUND")
	void product_notFound() {
		User user = newUser();
		Product empty = fixtures.product();

		assertThatThrownBy(() -> adviceService.advise(user.getId(), product(empty.getId())))
				.satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.NOT_FOUND));
		assertThatThrownBy(() -> adviceService.advise(user.getId(), product(Long.MAX_VALUE)))
				.satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.NOT_FOUND));
	}

	@Test
	@DisplayName("붙여넣기: 피부 상태 주의 성분이 있으면 CAUTION, 루틴 충돌 미리보기도 함께")
	void text_skinAndRoutine() {
		User user = newUser();
		skinProfileService.put(user.getId(), Set.of(SkinType.DRY, SkinType.SENSITIVE));
		Long aha = shelfItem(user, Verdict.UNRATED, "정제수", "글라이콜릭애씨드");
		routineService.create(user.getId(), new RoutineRequest("저녁 각질", RoutineSlot.EVENING, Set.of(),
				List.of(new StepRequest(aha, StepType.TONER))));

		AdviceResponse response = adviceService.advise(user.getId(), text("정제수, 레티놀\n향료"));

		assertThat(response.verdict()).isEqualTo(AdviceVerdict.CAUTION);
		assertThat(reason(response, ReasonType.SKIN_TYPE).ingredients()).containsExactly("향료");
		AdviceReasonResponse conflict = reason(response, ReasonType.ROUTINE_CONFLICT);
		assertThat(conflict.message()).contains("저녁 각질");
		assertThat(conflict.relatedShelfItemIds()).containsExactly(aha);
		assertThat(response.matchedCount() + response.unmatchedCount()).isEqualTo(3);
		assertThat(response.product()).isNull();
		assertThat(response.ingredients()).hasSize(3);
	}

	@Test
	@DisplayName("붙여넣기: 잘 맞았던 제품과 50% 이상 겹치고 기록이 2개 이상이면 GOOD_FIT")
	void text_goodFit() {
		User user = newUser();
		String a = "보습A" + ProductFixtures.unique();
		String b = "보습B" + ProductFixtures.unique();
		shelfItem(user, Verdict.GOOD, a, "정제수");
		shelfItem(user, Verdict.GOOD, b);

		AdviceResponse response = adviceService.advise(user.getId(), text(a + "," + b + ",정제수,새성분"));

		assertThat(response.verdict()).isEqualTo(AdviceVerdict.GOOD_FIT);
		assertThat(reason(response, ReasonType.GOOD_OVERLAP).level()).isEqualTo(AdviceLevel.POSITIVE);
	}

	@Test
	@DisplayName("붙여넣기: 200개를 넘으면 ADVICE_TOO_MANY_INGREDIENTS")
	void text_tooMany() {
		User user = newUser();
		String many = String.join(",", java.util.Collections.nCopies(201, "a"));

		assertThatThrownBy(() -> adviceService.advise(user.getId(), text(many)))
				.satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.ADVICE_TOO_MANY_INGREDIENTS));
	}

	@Test
	@DisplayName("입력이 없거나 둘 이상이면 INVALID_REQUEST")
	void exactlyOne() {
		User user = newUser();

		assertThatThrownBy(() -> adviceService.advise(user.getId(), new AdviceRequest(null, null)))
				.satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_REQUEST));
		assertThatThrownBy(() -> adviceService.advise(user.getId(), new AdviceRequest(1L, "정제수")))
				.satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.INVALID_REQUEST));
	}
}
