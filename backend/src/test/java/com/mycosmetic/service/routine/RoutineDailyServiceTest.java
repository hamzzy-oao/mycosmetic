package com.mycosmetic.service.routine;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.catchThrowable;

import java.time.LocalDate;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;

import com.mycosmetic.dto.routine.RoutineCheckRequest;
import com.mycosmetic.dto.routine.RoutineRequest;
import com.mycosmetic.dto.routine.RoutineResponse;
import com.mycosmetic.dto.routine.RoutineStepResponse;
import com.mycosmetic.dto.routine.RoutineUsageResponse;
import com.mycosmetic.dto.routine.StepRequest;
import com.mycosmetic.dto.routine.TodayRoutinesResponse;
import com.mycosmetic.dto.routine.WarningResponse;
import com.mycosmetic.dto.shelf.UpdateShelfRequest;
import com.mycosmetic.entity.Product;
import com.mycosmetic.entity.RoutineCheck;
import com.mycosmetic.entity.RoutineSlot;
import com.mycosmetic.entity.StepType;
import com.mycosmetic.entity.User;
import com.mycosmetic.entity.Verdict;
import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.repository.RoutineCheckRepository;
import com.mycosmetic.repository.ShelfItemRepository;
import com.mycosmetic.repository.UserRepository;
import com.mycosmetic.service.shelf.ShelfService;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.ProductFixtures;
import com.mycosmetic.support.TestAuthHelper;

/** 루틴 탭 개편: 진단에서 저장, 오늘 사용 체크, 사용 일수. 실제 MySQL(mycosmetic_test)로 돌린다 */
class RoutineDailyServiceTest extends IntegrationTestSupport {

	@Autowired
	RoutineService routineService;

	@Autowired
	ShelfService shelfService;

	@Autowired
	RoutineCheckRepository checkRepository;

	@Autowired
	ShelfItemRepository shelfItemRepository;

	@Autowired
	UserRepository userRepository;

	@Autowired
	ProductFixtures fixtures;

	@Autowired
	TestAuthHelper auth;

	private User newUser() {
		return auth.createUser(TestAuthHelper.uniqueEmail());
	}

	/** 이름으로 단계를 추정할 수 있는 제품을 담는다 */
	private Long add(User user, String name, Object... lines) {
		Product product = fixtures.product(ProductFixtures.newBarcode(), "브랜드", name + " " + ProductFixtures.unique());
		return shelfService.add(user.getId(),
				fixtures.list(product, 1, lines.length == 0 ? new Object[] { "정제수" } : lines).getId()).id();
	}

	private static RoutineCheckRequest check(RoutineSlot slot, boolean checked, Long... ids) {
		return new RoutineCheckRequest(slot, List.of(ids), checked);
	}

	private static List<Long> stepIds(RoutineResponse r) {
		return r.steps().stream().map(RoutineStepResponse::shelfItemId).toList();
	}

	private static ErrorCode error(Runnable call) {
		return ((BusinessException) catchThrowable(call::run)).errorCode();
	}

	@Test
	@DisplayName("진단에서 저장: 초안대로 매일 하는 아침·저녁 루틴을 만든다")
	void saveFromDiagnosis_createsDailyRoutines() {
		User user = newUser();
		Long cleanser = add(user, "약산성 클렌징폼");
		Long cream = add(user, "세라 크림");
		Long sun = add(user, "톤업 선크림");

		List<RoutineResponse> saved = routineService.saveFromDiagnosis(user.getId());

		assertThat(saved).hasSize(2).allMatch(RoutineResponse::fromDiagnosis).allMatch(r -> r.days().isEmpty());
		assertThat(saved.get(0).slot()).isEqualTo(RoutineSlot.MORNING);
		assertThat(stepIds(saved.get(0))).containsExactly(cleanser, cream, sun);
		assertThat(saved.get(1).slot()).isEqualTo(RoutineSlot.EVENING);
		assertThat(stepIds(saved.get(1))).containsExactly(cleanser, cream);
	}

	@Test
	@DisplayName("진단에서 다시 저장하면 전에 진단에서 저장한 루틴만 바뀌고 직접 만든 루틴은 그대로, 루틴이 쌓이지 않는다")
	void saveFromDiagnosis_replacesOnlyDiagnosisRoutines() {
		User user = newUser();
		Long cream = add(user, "세라 크림");
		Long manual = routineService.create(user.getId(), new RoutineRequest("내 루틴", RoutineSlot.EVENING, null,
				List.of(new StepRequest(cream, StepType.CREAM)))).id();
		routineService.saveFromDiagnosis(user.getId());
		Long toner = add(user, "수분 토너");

		List<RoutineResponse> again = routineService.saveFromDiagnosis(user.getId());

		assertThat(again).hasSize(3);
		assertThat(again).filteredOn(r -> !r.fromDiagnosis()).extracting(RoutineResponse::id).containsExactly(manual);
		assertThat(again).filteredOn(RoutineResponse::fromDiagnosis)
				.allSatisfy(r -> assertThat(stepIds(r)).containsExactly(toner, cream));
	}

	@Test
	@DisplayName("진단 초안이 비면(스킨케어 제품 없음) INVALID_REQUEST이고 기존 루틴은 그대로")
	void saveFromDiagnosis_emptyDraft() {
		User user = newUser();

		assertThat(error(() -> routineService.saveFromDiagnosis(user.getId()))).isEqualTo(ErrorCode.INVALID_REQUEST);
		assertThat(routineService.list(user.getId())).isEmpty();
	}

	@Test
	@DisplayName("오늘 체크: 체크·다시 체크(그대로)·해제가 시간대별로 따로 되고 오늘 응답에 담긴다")
	void checkToday() {
		User user = newUser();
		Long toner = add(user, "수분 토너");
		Long cream = add(user, "세라 크림");

		TodayRoutinesResponse r = routineService.checkToday(user.getId(), check(RoutineSlot.MORNING, true, toner, cream));
		assertThat(r.checkedMorning()).containsExactlyInAnyOrder(toner, cream);
		assertThat(r.checkedEvening()).isEmpty();

		routineService.checkToday(user.getId(), check(RoutineSlot.MORNING, true, toner));
		r = routineService.checkToday(user.getId(), check(RoutineSlot.EVENING, true, cream));
		assertThat(r.checkedMorning()).hasSize(2);
		assertThat(r.checkedEvening()).containsExactly(cream);

		r = routineService.checkToday(user.getId(), check(RoutineSlot.MORNING, false, cream));
		assertThat(r.checkedMorning()).containsExactly(toner);
		assertThat(r.checkedEvening()).containsExactly(cream);
		assertThat(routineService.today(user.getId()).checkedMorning()).containsExactly(toner);
	}

	@Test
	@DisplayName("오늘 체크: 남의 화장대 항목이 섞이면 NOT_FOUND이고 아무것도 체크되지 않는다")
	void checkToday_otherUsersItem() {
		User a = newUser();
		User b = newUser();
		Long aItem = add(a, "세라 크림");
		Long bItem = add(b, "세라 크림");

		assertThat(error(() -> routineService.checkToday(b.getId(), check(RoutineSlot.MORNING, true, bItem, aItem))))
				.isEqualTo(ErrorCode.NOT_FOUND);
		assertThat(routineService.today(b.getId()).checkedMorning()).isEmpty();
		assertThat(routineService.today(a.getId()).checkedMorning()).isEmpty();
	}

	@Test
	@DisplayName("오늘 경고: 오늘 아침 루틴들을 묶어 보고, 저녁과의 같은 날 조합도 한 번만 알린다")
	void todayWarnings() {
		User user = newUser();
		Long retinol = add(user, "레티놀 세럼", "레티놀");
		Long aha = add(user, "각질 토너", "글라이콜릭애씨드");
		Long aha2 = add(user, "필링 앰플", "글라이콜릭애씨드");
		routineService.create(user.getId(), new RoutineRequest("밤 A", RoutineSlot.EVENING, null,
				List.of(new StepRequest(retinol, StepType.SERUM))));
		routineService.create(user.getId(), new RoutineRequest("밤 B", RoutineSlot.EVENING, null,
				List.of(new StepRequest(aha, StepType.TONER))));
		routineService.create(user.getId(), new RoutineRequest("아침", RoutineSlot.MORNING, null,
				List.of(new StepRequest(aha2, StepType.AMPOULE))));

		List<WarningResponse> warnings = routineService.today(user.getId()).warnings();

		assertThat(warnings).extracting(WarningResponse::code)
				.containsExactly("RETINOID_AHA", "MORNING_SUN_SENSITIVE", "SAME_DAY");
		assertThat(warnings.get(0).items()).containsExactlyInAnyOrder(retinol, aha);
	}

	@Test
	@DisplayName("사용 일수: 최근 28일 안에서 날짜 수로 세고(아침·저녁 같은 날은 하루), 14일 이상이면 꾸준히 쓴 제품")
	void usage() {
		User user = newUser();
		Long steady = add(user, "세라 크림");
		shelfService.update(user.getId(), steady, new UpdateShelfRequest(Verdict.GOOD, null, null, null));
		Long rare = add(user, "수분 토너");
		LocalDate today = LocalDate.now(RoutineService.SEOUL);
		var userRef = userRepository.getReferenceById(user.getId());
		var steadyItem = shelfItemRepository.findById(steady).orElseThrow();
		var rareItem = shelfItemRepository.findById(rare).orElseThrow();
		for (int i = 0; i < 14; i++) {
			checkRepository.save(RoutineCheck.of(userRef, steadyItem, today.minusDays(i), RoutineSlot.MORNING));
		}
		checkRepository.save(RoutineCheck.of(userRef, steadyItem, today, RoutineSlot.EVENING));
		checkRepository.save(RoutineCheck.of(userRef, rareItem, today.minusDays(27), RoutineSlot.EVENING));
		checkRepository.save(RoutineCheck.of(userRef, rareItem, today.minusDays(28), RoutineSlot.EVENING));

		RoutineUsageResponse usage = routineService.usage(user.getId());

		assertThat(usage.days()).isEqualTo(28);
		assertThat(usage.items()).extracting(RoutineUsageResponse.Item::shelfItemId).containsExactly(steady, rare);
		assertThat(usage.items().get(0).daysUsed()).isEqualTo(14);
		assertThat(usage.items().get(0).steady()).isTrue();
		assertThat(usage.items().get(0).verdict()).isEqualTo(Verdict.GOOD);
		assertThat(usage.items().get(1).daysUsed()).isEqualTo(1);
		assertThat(usage.items().get(1).steady()).isFalse();
		assertThat(routineService.usage(newUser().getId()).items()).isEmpty();
	}
}
