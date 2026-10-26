package com.mycosmetic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.jayway.jsonpath.JsonPath;
import com.mycosmetic.dto.routine.RoutineRequest;
import com.mycosmetic.dto.routine.RoutineResponse;
import com.mycosmetic.dto.routine.StepRequest;
import com.mycosmetic.dto.shelf.UpdateShelfRequest;
import com.mycosmetic.entity.RoutineSlot;
import com.mycosmetic.entity.StepType;
import com.mycosmetic.entity.User;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.repository.RoutineRepository;
import com.mycosmetic.service.routine.RoutineService;
import com.mycosmetic.service.shelf.ShelfService;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.ProductFixtures;
import com.mycosmetic.support.TestAuthHelper;

/** 루틴 API 🔒 — 엔드포인트마다 401, (상태 변경·POST) CSRF 403, 남의 데이터 404, 정상 */
class RoutineControllerSecurityTest extends IntegrationTestSupport {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TestAuthHelper auth;

	@Autowired
	ProductFixtures fixtures;

	@Autowired
	ShelfService shelfService;

	@Autowired
	RoutineService routineService;

	@Autowired
	RoutineRepository routineRepository;

	private User newUser() {
		return auth.createUser(TestAuthHelper.uniqueEmail());
	}

	private Long itemOf(User user, String... ingredients) {
		return shelfService.add(user.getId(), fixtures.list(fixtures.product(), 1, (Object[]) ingredients).getId())
				.id();
	}

	/** user의 아침 루틴(항목 하나) ID */
	private Long routineOf(User user) {
		return routineService.create(user.getId(), new RoutineRequest("A 루틴", RoutineSlot.MORNING, null,
				List.of(new StepRequest(itemOf(user, "정제수"), StepType.TONER)))).id();
	}

	private static String body(String name, Long... itemIds) {
		StringBuilder steps = new StringBuilder();
		for (Long id : itemIds) {
			if (!steps.isEmpty()) {
				steps.append(',');
			}
			steps.append("{\"shelfItemId\": %d, \"stepType\": \"SERUM\"}".formatted(id));
		}
		return "{\"name\": \"%s\", \"slot\": \"EVENING\", \"days\": [\"MONDAY\"], \"steps\": [%s]}".formatted(name, steps);
	}

	private static void expect401(ResultActions result) throws Exception {
		result.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").value(ErrorCode.UNAUTHORIZED.message()))
				.andExpect(jsonPath("$.traceId").value(matchesPattern("[0-9a-f]{8}")));
	}

	private static void expectCsrf403(ResultActions result) throws Exception {
		result.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
	}

	private static void expect404(ResultActions result) throws Exception {
		result.andExpect(status().isNotFound()).andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	/** owner의 루틴이 routineOf로 만든 그대로인지 DB에서 다시 읽어 확인한다 */
	private void assertUnchanged(User owner, Long routineId) {
		RoutineResponse r = routineService.get(owner.getId(), routineId);
		assertThat(r.name()).isEqualTo("A 루틴");
		assertThat(r.slot()).isEqualTo(RoutineSlot.MORNING);
		assertThat(r.steps()).hasSize(1);
	}

	// ---- 401 ----

	@Test
	@DisplayName("로그인하지 않으면 모든 루틴 API가 401")
	void anonymous_401() throws Exception {
		User owner = newUser();
		Long id = routineOf(owner);
		expect401(mockMvc.perform(get("/api/routines")));
		expect401(mockMvc.perform(get("/api/routines/today")));
		expect401(mockMvc.perform(get("/api/routines/{id}", id)));
		expect401(mockMvc.perform(get("/api/routines/{id}/check", id)));
		expect401(mockMvc.perform(post("/api/routines").with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(body("x"))));
		expect401(mockMvc.perform(post("/api/routines/check").with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(body("x"))));
		expect401(mockMvc.perform(put("/api/routines/{id}", id).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(body("x"))));
		expect401(mockMvc.perform(delete("/api/routines/{id}", id).with(csrf())));
		assertUnchanged(owner, id);
	}

	// ---- CSRF 403 ----

	@Test
	@DisplayName("CSRF 토큰이 없으면 POST·PUT·DELETE가 403이고 아무것도 바뀌지 않는다")
	void withoutCsrf_403() throws Exception {
		User user = newUser();
		Long id = routineOf(user);
		var cookie = auth.accessTokenCookie(user);

		expectCsrf403(mockMvc.perform(post("/api/routines").cookie(cookie)
				.contentType(MediaType.APPLICATION_JSON).content(body("새 루틴"))));
		expectCsrf403(mockMvc.perform(post("/api/routines/check").cookie(cookie)
				.contentType(MediaType.APPLICATION_JSON).content(body("x"))));
		expectCsrf403(mockMvc.perform(put("/api/routines/{id}", id).cookie(cookie)
				.contentType(MediaType.APPLICATION_JSON).content(body("바뀜"))));
		expectCsrf403(mockMvc.perform(delete("/api/routines/{id}", id).cookie(cookie)));

		assertUnchanged(user, id);
		assertThat(routineService.list(user.getId())).hasSize(1);
	}

	// ---- 남의 데이터 404 ----

	@Test
	@DisplayName("B가 A의 루틴을 조회·검사·수정·삭제하면 404이고 A의 루틴은 그대로")
	void otherUsersRoutine_404() throws Exception {
		User a = newUser();
		User b = newUser();
		Long id = routineOf(a);
		var bCookie = auth.accessTokenCookie(b);

		expect404(mockMvc.perform(get("/api/routines/{id}", id).cookie(bCookie)));
		expect404(mockMvc.perform(get("/api/routines/{id}/check", id).cookie(bCookie)));
		expect404(mockMvc.perform(put("/api/routines/{id}", id).with(csrf()).cookie(bCookie)
				.contentType(MediaType.APPLICATION_JSON).content(body("바뀜"))));
		expect404(mockMvc.perform(post("/api/routines/check").param("routineId", id.toString()).with(csrf())
				.cookie(bCookie).contentType(MediaType.APPLICATION_JSON).content(body("x"))));
		expect404(mockMvc.perform(delete("/api/routines/{id}", id).with(csrf()).cookie(bCookie)));

		assertUnchanged(a, id);
		mockMvc.perform(get("/api/routines").cookie(bCookie))
				.andExpect(status().isOk()).andExpect(jsonPath("$.length()").value(0));
	}

	@Test
	@DisplayName("B가 A의 화장대 항목으로 루틴을 만들거나 고치거나 검사하면 404이고 아무것도 생기거나 바뀌지 않는다")
	void otherUsersShelfItem_404() throws Exception {
		User a = newUser();
		User b = newUser();
		Long aItem = itemOf(a, "정제수");
		Long bRoutine = routineOf(b);
		var bCookie = auth.accessTokenCookie(b);

		expect404(mockMvc.perform(post("/api/routines").with(csrf()).cookie(bCookie)
				.contentType(MediaType.APPLICATION_JSON).content(body("훔친 루틴", aItem))));
		expect404(mockMvc.perform(put("/api/routines/{id}", bRoutine).with(csrf()).cookie(bCookie)
				.contentType(MediaType.APPLICATION_JSON).content(body("훔친 루틴", aItem))));
		expect404(mockMvc.perform(post("/api/routines/check").with(csrf()).cookie(bCookie)
				.contentType(MediaType.APPLICATION_JSON).content(body("x", aItem))));

		assertThat(routineService.list(b.getId())).hasSize(1);
		assertUnchanged(b, bRoutine);
		assertThat(routineService.list(a.getId())).isEmpty();
	}

	// ---- 정상 ----

	@Test
	@DisplayName("본인: 만들기 201 → 목록·조회 200 → 검사 200 → 수정 200 → 삭제 204")
	void own_fullFlow() throws Exception {
		User user = newUser();
		var cookie = auth.accessTokenCookie(user);
		Long retinol = itemOf(user, "레티놀");
		Long aha = itemOf(user, "글라이콜릭애씨드");

		String created = mockMvc.perform(post("/api/routines").with(csrf()).cookie(cookie)
				.contentType(MediaType.APPLICATION_JSON).content(body("밤 루틴", retinol, aha)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.name").value("밤 루틴"))
				.andExpect(jsonPath("$.slot").value("EVENING"))
				.andExpect(jsonPath("$.days[0]").value("MONDAY"))
				.andExpect(jsonPath("$.steps.length()").value(2))
				.andExpect(jsonPath("$.steps[0].shelfItemId").value(retinol))
				.andExpect(jsonPath("$.steps[0].stepType").value("SERUM"))
				.andExpect(jsonPath("$.steps[0].position").value(1))
				.andExpect(jsonPath("$.steps[0].productName").isString())
				.andReturn().getResponse().getContentAsString();
		Long id = ((Number) JsonPath.read(created, "$.id")).longValue();

		mockMvc.perform(get("/api/routines").cookie(cookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(id));
		mockMvc.perform(get("/api/routines/{id}", id).cookie(cookie))
				.andExpect(status().isOk()).andExpect(jsonPath("$.steps[1].shelfItemId").value(aha));
		mockMvc.perform(get("/api/routines/{id}/check", id).cookie(cookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.warnings.length()").value(1))
				.andExpect(jsonPath("$.warnings[0].level").value("WARNING"))
				.andExpect(jsonPath("$.warnings[0].code").value("RETINOID_AHA"))
				.andExpect(jsonPath("$.warnings[0].message").isString())
				.andExpect(jsonPath("$.warnings[0].items.length()").value(2))
				.andExpect(jsonPath("$.suggestedOrder[0]").value(retinol));
		mockMvc.perform(post("/api/routines/check").param("routineId", id.toString()).with(csrf()).cookie(cookie)
				.contentType(MediaType.APPLICATION_JSON).content(body("미리", retinol)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.warnings.length()").value(0))
				.andExpect(jsonPath("$.suggestedOrder[0]").value(retinol));
		mockMvc.perform(put("/api/routines/{id}", id).with(csrf()).cookie(cookie)
				.contentType(MediaType.APPLICATION_JSON).content(body("레티놀만", retinol)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("레티놀만"))
				.andExpect(jsonPath("$.steps.length()").value(1));
		mockMvc.perform(get("/api/routines/today").cookie(cookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.date").isString())
				.andExpect(jsonPath("$.dayOfWeek").isString())
				.andExpect(jsonPath("$.morning").isArray())
				.andExpect(jsonPath("$.evening").isArray());
		mockMvc.perform(delete("/api/routines/{id}", id).with(csrf()).cookie(cookie))
				.andExpect(status().isNoContent());
		assertThat(routineRepository.findById(id)).isEmpty();
	}

	@Test
	@DisplayName("본인: 이름이 비었거나 51자, 모르는 단계 종류, steps가 없으면 400 INVALID_REQUEST")
	void own_invalid_400() throws Exception {
		User user = newUser();
		var cookie = auth.accessTokenCookie(user);
		Long item = itemOf(user, "정제수");
		for (String content : List.of(body(" "), body("가".repeat(51)),
				"{\"name\": \"x\", \"slot\": \"EVENING\", \"steps\": [{\"shelfItemId\": %d, \"stepType\": \"FOO\"}]}"
						.formatted(item),
				"{\"name\": \"x\", \"slot\": \"EVENING\"}", "{\"name\": \"x\", \"steps\": []}")) {
			mockMvc.perform(post("/api/routines").with(csrf()).cookie(cookie)
					.contentType(MediaType.APPLICATION_JSON).content(content))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		}
		assertThat(routineService.list(user.getId())).isEmpty();
	}

	// ---- 오늘 사용 체크·사용 일수·진단에서 저장 ----

	private static String checkBody(boolean checked, Long... itemIds) {
		return "{\"slot\": \"MORNING\", \"checked\": %s, \"shelfItemIds\": %s}".formatted(checked,
				Arrays.toString(itemIds));
	}

	@Test
	@DisplayName("오늘 체크·사용 일수·진단에서 저장: 로그인하지 않으면 401, CSRF 없으면 403이고 아무것도 생기지 않는다")
	void daily_401_and_csrf403() throws Exception {
		User user = newUser();
		Long item = itemOf(user, "정제수");
		var cookie = auth.accessTokenCookie(user);

		expect401(mockMvc.perform(get("/api/routines/usage")));
		expect401(mockMvc.perform(put("/api/routines/today/checks").with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(checkBody(true, item))));
		expect401(mockMvc.perform(post("/api/routines/from-diagnosis").with(csrf())));

		expectCsrf403(mockMvc.perform(put("/api/routines/today/checks").cookie(cookie)
				.contentType(MediaType.APPLICATION_JSON).content(checkBody(true, item))));
		expectCsrf403(mockMvc.perform(post("/api/routines/from-diagnosis").cookie(cookie)));

		assertThat(routineService.today(user.getId()).checkedMorning()).isEmpty();
		assertThat(routineService.list(user.getId())).isEmpty();
	}

	@Test
	@DisplayName("B가 A의 화장대 항목을 오늘 체크하면 404이고 A·B 모두 체크가 생기지 않는다")
	void daily_otherUsersItem_404() throws Exception {
		User a = newUser();
		User b = newUser();
		Long aItem = itemOf(a, "정제수");
		var bCookie = auth.accessTokenCookie(b);

		expect404(mockMvc.perform(put("/api/routines/today/checks").with(csrf()).cookie(bCookie)
				.contentType(MediaType.APPLICATION_JSON).content(checkBody(true, aItem))));

		assertThat(routineService.today(a.getId()).checkedMorning()).isEmpty();
		assertThat(routineService.today(b.getId()).checkedMorning()).isEmpty();
		mockMvc.perform(get("/api/routines/usage").cookie(bCookie))
				.andExpect(status().isOk()).andExpect(jsonPath("$.items.length()").value(0));
	}

	@Test
	@DisplayName("본인: 진단에서 저장 200 → 오늘 체크 200 → 사용 일수 200 → 체크 해제 200, 빈 목록은 400")
	void daily_own_flow() throws Exception {
		User user = newUser();
		var cookie = auth.accessTokenCookie(user);
		Long item = itemOf(user, "정제수");
		shelfService.update(user.getId(), item,
				new UpdateShelfRequest(null, null, null, null, null, null, null, null, "CREAM", null));

		mockMvc.perform(post("/api/routines/from-diagnosis").with(csrf()).cookie(cookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].fromDiagnosis").value(true))
				.andExpect(jsonPath("$[0].steps[0].shelfItemId").value(item));
		mockMvc.perform(put("/api/routines/today/checks").with(csrf()).cookie(cookie)
				.contentType(MediaType.APPLICATION_JSON).content(checkBody(true, item)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.checkedMorning[0]").value(item))
				.andExpect(jsonPath("$.checkedEvening.length()").value(0))
				.andExpect(jsonPath("$.warnings").isArray());
		mockMvc.perform(get("/api/routines/usage").cookie(cookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.days").value(28))
				.andExpect(jsonPath("$.items[0].shelfItemId").value(item))
				.andExpect(jsonPath("$.items[0].daysUsed").value(1));
		mockMvc.perform(put("/api/routines/today/checks").with(csrf()).cookie(cookie)
				.contentType(MediaType.APPLICATION_JSON).content(checkBody(false, item)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.checkedMorning.length()").value(0));
		mockMvc.perform(put("/api/routines/today/checks").with(csrf()).cookie(cookie)
				.contentType(MediaType.APPLICATION_JSON).content("{\"slot\": \"MORNING\", \"checked\": true, \"shelfItemIds\": []}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}
}
