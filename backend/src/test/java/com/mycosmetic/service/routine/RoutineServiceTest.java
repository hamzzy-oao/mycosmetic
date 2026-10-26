package com.mycosmetic.service.routine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.assertj.core.api.ThrowableAssert.ThrowingCallable;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.mycosmetic.dto.routine.RoutineCheckResponse;
import com.mycosmetic.dto.routine.RoutineRequest;
import com.mycosmetic.dto.routine.RoutineResponse;
import com.mycosmetic.dto.routine.RoutineStepResponse;
import com.mycosmetic.dto.routine.StepRequest;
import com.mycosmetic.dto.routine.TodayRoutinesResponse;
import com.mycosmetic.dto.routine.WarningResponse;
import com.mycosmetic.entity.Product;
import com.mycosmetic.entity.RoutineSlot;
import com.mycosmetic.entity.StepType;
import com.mycosmetic.entity.User;
import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.repository.RoutineRepository;
import com.mycosmetic.service.shelf.ShelfService;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.ProductFixtures;
import com.mycosmetic.support.TestAuthHelper;

/** 루틴 서비스. 성분 버전·화장대·외래 키 CASCADE까지 보려고 실제 MySQL(mycosmetic_test)로 돌린다 */
class RoutineServiceTest extends IntegrationTestSupport {

	@Autowired
	RoutineService routineService;

	@Autowired
	ShelfService shelfService;

	@Autowired
	RoutineRepository routineRepository;

	@Autowired
	ProductFixtures fixtures;

	@Autowired
	TestAuthHelper auth;

	private User newUser() {
		return auth.createUser(TestAuthHelper.uniqueEmail());
	}

	/** 이 성분들로 된 새 제품을 user의 화장대에 담고 항목 ID */
	private Long item(User user, Object... ingredients) {
		return shelfService.add(user.getId(), fixtures.list(fixtures.product(), 1, ingredients).getId()).id();
	}

	private static RoutineRequest request(String name, RoutineSlot slot, Set<DayOfWeek> days, Object... idAndTypes) {
		List<StepRequest> steps = new ArrayList<>();
		for (int i = 0; i < idAndTypes.length; i += 2) {
			steps.add(new StepRequest((Long) idAndTypes[i], (StepType) idAndTypes[i + 1]));
		}
		return new RoutineRequest(name, slot, days, steps);
	}

	private static BusinessException error(ThrowingCallable call) {
		return (BusinessException) catchThrowable(call);
	}

	@Test
	@DisplayName("만들면 요청 순서대로 1부터 position을 매기고 제품 이름을 함께 준다. 이름 앞뒤 공백은 뗀다")
	void create() {
		User user = newUser();
		Long toner = item(user, "정제수");
		Long cream = item(user, "정제수");

		RoutineResponse r = routineService.create(user.getId(), request("  저녁 루틴 ", RoutineSlot.EVENING,
				EnumSet.of(DayOfWeek.FRIDAY, DayOfWeek.MONDAY), cream, StepType.CREAM, toner, StepType.TONER));

		assertThat(r.id()).isNotNull();
		assertThat(r.name()).isEqualTo("저녁 루틴");
		assertThat(r.slot()).isEqualTo(RoutineSlot.EVENING);
		assertThat(r.days()).containsExactly(DayOfWeek.MONDAY, DayOfWeek.FRIDAY);
		assertThat(r.steps()).extracting(RoutineStepResponse::shelfItemId).containsExactly(cream, toner);
		assertThat(r.steps()).extracting(RoutineStepResponse::position).containsExactly(1, 2);
		assertThat(r.steps().get(0).productName()).startsWith("토너 ");
		assertThat(r.steps().get(0).brand()).isEqualTo("브랜드");
		assertThat(routineService.get(user.getId(), r.id())).isEqualTo(r);
		assertThat(routineService.list(user.getId())).extracting(RoutineResponse::id).containsExactly(r.id());
	}

	@Test
	@DisplayName("days가 null이면 매일(빈 집합), 단계 없이도 만들 수 있다")
	void create_nullDaysNoSteps() {
		User user = newUser();
		RoutineResponse r = routineService.create(user.getId(), request("빈 루틴", RoutineSlot.MORNING, null));

		assertThat(r.days()).isEmpty();
		assertThat(r.steps()).isEmpty();
	}

	@Test
	@DisplayName("남의 화장대 항목이나 없는 항목은 NOT_FOUND, 같은 항목을 두 번 넣으면 INVALID_REQUEST")
	void create_rejectsForeignAndDuplicateItems() {
		User a = newUser();
		User b = newUser();
		Long aItem = item(a, "정제수");
		Long bItem = item(b, "정제수");

		assertThat(error(() -> routineService.create(a.getId(),
				request("r", RoutineSlot.MORNING, null, aItem, StepType.TONER, bItem, StepType.CREAM))).errorCode())
				.isEqualTo(ErrorCode.NOT_FOUND);
		assertThat(error(() -> routineService.create(a.getId(),
				request("r", RoutineSlot.MORNING, null, Long.MAX_VALUE, StepType.TONER))).errorCode())
				.isEqualTo(ErrorCode.NOT_FOUND);
		assertThat(error(() -> routineService.create(a.getId(),
				request("r", RoutineSlot.MORNING, null, aItem, StepType.TONER, aItem, StepType.CREAM))).errorCode())
				.isEqualTo(ErrorCode.INVALID_REQUEST);
		assertThat(routineService.list(a.getId())).isEmpty();
	}

	@Test
	@DisplayName("수정은 이름·시간대·요일·단계를 통째로 바꾼다")
	void update_replacesAll() {
		User user = newUser();
		Long x = item(user, "정제수");
		Long y = item(user, "정제수");
		Long z = item(user, "정제수");
		Long id = routineService.create(user.getId(),
				request("아침", RoutineSlot.MORNING, null, x, StepType.TONER, y, StepType.SERUM)).id();

		RoutineResponse r = routineService.update(user.getId(), id, request("저녁", RoutineSlot.EVENING,
				EnumSet.of(DayOfWeek.SUNDAY), z, StepType.CLEANSER, x, StepType.CREAM));

		assertThat(r.name()).isEqualTo("저녁");
		assertThat(r.slot()).isEqualTo(RoutineSlot.EVENING);
		assertThat(r.days()).containsExactly(DayOfWeek.SUNDAY);
		assertThat(r.steps()).extracting(RoutineStepResponse::shelfItemId).containsExactly(z, x);
		assertThat(r.steps()).extracting(RoutineStepResponse::stepType)
				.containsExactly(StepType.CLEANSER, StepType.CREAM);
		assertThat(routineService.get(user.getId(), id).steps()).extracting(RoutineStepResponse::position)
				.containsExactly(1, 2);
	}

	@Test
	@DisplayName("남의 루틴은 조회·수정·삭제·검사 모두 NOT_FOUND이고 그대로 남는다")
	void otherUsersRoutine_notFound() {
		User a = newUser();
		User b = newUser();
		Long id = routineService.create(a.getId(), request("A", RoutineSlot.MORNING, null, item(a, "정제수"),
				StepType.TONER)).id();
		RoutineRequest change = request("B", RoutineSlot.EVENING, null);

		assertThatThrownBy(() -> routineService.get(b.getId(), id)).isInstanceOf(BusinessException.class);
		assertThat(error(() -> routineService.update(b.getId(), id, change)).errorCode()).isEqualTo(ErrorCode.NOT_FOUND);
		assertThat(error(() -> routineService.delete(b.getId(), id)).errorCode()).isEqualTo(ErrorCode.NOT_FOUND);
		assertThat(error(() -> routineService.check(b.getId(), id)).errorCode()).isEqualTo(ErrorCode.NOT_FOUND);
		assertThat(error(() -> routineService.checkDraft(b.getId(), id, change)).errorCode())
				.isEqualTo(ErrorCode.NOT_FOUND);
		RoutineResponse after = routineService.get(a.getId(), id);
		assertThat(after.name()).isEqualTo("A");
		assertThat(after.steps()).hasSize(1);
	}

	@Test
	@DisplayName("삭제하면 루틴과 단계가 사라지고 화장대 항목은 남는다")
	void delete() {
		User user = newUser();
		Long x = item(user, "정제수");
		Long id = routineService.create(user.getId(), request("r", RoutineSlot.MORNING, null, x, StepType.TONER)).id();

		routineService.delete(user.getId(), id);

		assertThat(routineRepository.findById(id)).isEmpty();
		assertThat(shelfService.list(user.getId())).hasSize(1);
	}

	@Test
	@DisplayName("화장대 항목을 지우면 그 항목이 들어간 루틴 단계도 사라진다(외래 키 CASCADE)")
	void shelfItemRemoved_stepRemoved() {
		User user = newUser();
		Long x = item(user, "정제수");
		Long y = item(user, "정제수");
		Long id = routineService.create(user.getId(),
				request("r", RoutineSlot.MORNING, null, x, StepType.TONER, y, StepType.CREAM)).id();

		shelfService.remove(user.getId(), x);

		assertThat(routineService.get(user.getId(), id).steps()).extracting(RoutineStepResponse::shelfItemId)
				.containsExactly(y);
	}

	@Test
	@DisplayName("검사: 표준명·원문으로 그룹을 찾아 경고하고 권장 순서를 준다")
	void check_warningsAndSuggestedOrder() {
		User user = newUser();
		Long retinol = item(user, "정제수", "레티놀");
		Long aha = item(user, fixtures.ingredient("글라이콜릭애씨드"));
		Long toner = item(user, "정제수");
		Long id = routineService.create(user.getId(), request("밤", RoutineSlot.EVENING, null,
				retinol, StepType.SERUM, aha, StepType.ESSENCE, toner, StepType.TONER)).id();

		RoutineCheckResponse check = routineService.check(user.getId(), id);

		assertThat(check.warnings()).singleElement().satisfies(w -> {
			assertThat(w.level()).isEqualTo(WarningLevel.WARNING);
			assertThat(w.code()).isEqualTo("RETINOID_AHA");
			assertThat(w.items()).containsExactlyInAnyOrder(retinol, aha);
		});
		assertThat(check.suggestedOrder()).containsExactly(toner, aha, retinol);
	}

	@Test
	@DisplayName("검사는 대표 버전이 아니라 내가 담은 버전의 성분으로 한다")
	void check_usesMyPinnedVersion() {
		User user = newUser();
		Product product = fixtures.product();
		Long myListId = fixtures.list(product, 1, "정제수", "살리실릭애씨드").getId();
		fixtures.list(product, 5, "정제수");   // 더 많이 확인된 새 버전(대표)에는 BHA가 없다
		Long bha = shelfService.add(user.getId(), myListId).id();
		Long retinol = item(user, "레티놀");
		Long id = routineService.create(user.getId(), request("밤", RoutineSlot.EVENING, null,
				bha, StepType.TONER, retinol, StepType.SERUM)).id();

		assertThat(routineService.check(user.getId(), id).warnings()).extracting(WarningResponse::code)
				.containsExactly("RETINOID_BHA");
	}

	@Test
	@DisplayName("검사: 아침 루틴의 레티노이드는 주의, 같은 요일 저녁 루틴과의 충돌은 '같은 날' 정보")
	void check_morningAndSameDay() {
		User user = newUser();
		Long retinol = item(user, "레티닐팔미테이트");
		Long bpo = item(user, "벤조일퍼옥사이드");
		Long morning = routineService.create(user.getId(), request("아침", RoutineSlot.MORNING,
				EnumSet.of(DayOfWeek.MONDAY), retinol, StepType.SERUM)).id();
		routineService.create(user.getId(), request("월요일 밤", RoutineSlot.EVENING, EnumSet.of(DayOfWeek.MONDAY),
				bpo, StepType.SERUM));

		RoutineCheckResponse check = routineService.check(user.getId(), morning);

		assertThat(check.warnings()).extracting(WarningResponse::code)
				.containsExactly("MORNING_SUN_SENSITIVE", "SAME_DAY");
		assertThat(check.warnings().get(1).message()).contains("월요일 밤");
		assertThat(check.warnings().get(1).items()).containsExactlyInAnyOrder(retinol, bpo);
	}

	@Test
	@DisplayName("미리 보기: 저장하지 않은 단계로 검사하고, 고치는 중인 루틴은 '같은 날' 비교에서 뺀다")
	void checkDraft() {
		User user = newUser();
		Long retinol = item(user, "레티놀");
		Long aha = item(user, "락틱애씨드");
		Long sunscreen = item(user, "정제수");
		Long id = routineService.create(user.getId(), request("아침", RoutineSlot.MORNING, null,
				aha, StepType.TONER)).id();

		RoutineCheckResponse draftOfSame = routineService.checkDraft(user.getId(), id, request("저녁으로",
				RoutineSlot.EVENING, null, sunscreen, StepType.SUNSCREEN, retinol, StepType.SERUM));
		RoutineCheckResponse newDraft = routineService.checkDraft(user.getId(), null, request("새 저녁",
				RoutineSlot.EVENING, null, sunscreen, StepType.SUNSCREEN, retinol, StepType.SERUM));

		assertThat(draftOfSame.warnings()).extracting(WarningResponse::code).containsExactly("EVENING_SUNSCREEN");
		assertThat(draftOfSame.suggestedOrder()).containsExactly(retinol, sunscreen);
		assertThat(newDraft.warnings()).extracting(WarningResponse::code)
				.containsExactly("EVENING_SUNSCREEN", "SAME_DAY");
		assertThat(error(() -> routineService.checkDraft(user.getId(), null,
				request("x", RoutineSlot.EVENING, null, retinol, StepType.SERUM, retinol, StepType.CREAM))).errorCode())
				.isEqualTo(ErrorCode.INVALID_REQUEST);
	}

	@Test
	@DisplayName("오늘: 오늘(Asia/Seoul) 요일에 도는 루틴만 아침·저녁으로 나눠 준다(요일이 비면 매일)")
	void today() {
		User user = newUser();
		DayOfWeek today = LocalDate.now(RoutineService.SEOUL).getDayOfWeek();
		Long everyDay = routineService.create(user.getId(), request("매일 아침", RoutineSlot.MORNING, null)).id();
		Long todayEvening = routineService.create(user.getId(),
				request("오늘 저녁", RoutineSlot.EVENING, EnumSet.of(today))).id();
		routineService.create(user.getId(), request("내일 저녁", RoutineSlot.EVENING, EnumSet.of(today.plus(1))));

		TodayRoutinesResponse r = routineService.today(user.getId());

		assertThat(r.dayOfWeek()).isEqualTo(today);
		assertThat(r.date().getDayOfWeek()).isEqualTo(today);
		assertThat(r.morning()).extracting(RoutineResponse::id).containsExactly(everyDay);
		assertThat(r.evening()).extracting(RoutineResponse::id).containsExactly(todayEvening);
	}
}
