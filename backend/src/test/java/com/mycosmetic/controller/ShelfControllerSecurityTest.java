package com.mycosmetic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.mycosmetic.dto.shelf.UpdateShelfRequest;
import com.mycosmetic.entity.IngredientList;
import com.mycosmetic.entity.PersonalColor;
import com.mycosmetic.entity.Product;
import com.mycosmetic.entity.ShelfItem;
import com.mycosmetic.entity.StepType;
import com.mycosmetic.entity.User;
import com.mycosmetic.entity.Verdict;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.repository.ShelfItemRepository;
import com.mycosmetic.service.shelf.ShelfService;
import com.mycosmetic.service.skin.SkinProfileService;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.ProductFixtures;
import com.mycosmetic.support.TestAuthHelper;

/** 내 화장대 API — POST·PATCH·DELETE 보안 테스트 4종, GET 1·4번 + 남의 항목이 섞이지 않음 */
class ShelfControllerSecurityTest extends IntegrationTestSupport {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TestAuthHelper auth;

	@Autowired
	ProductFixtures fixtures;

	@Autowired
	ShelfService shelfService;

	@Autowired
	ShelfItemRepository shelfItemRepository;

	@Autowired
	SkinProfileService skinProfileService;

	private User newUser() {
		return auth.createUser(TestAuthHelper.uniqueEmail());
	}

	private IngredientList newList() {
		return fixtures.list(fixtures.product(), 1, "정제수");
	}

	/** user의 화장대에 새 항목을 담고 그 ID */
	private Long shelfItemOf(User user) {
		return shelfService.add(user.getId(), newList().getId()).id();
	}

	private static String addBody(Long listId) {
		return "{\"ingredientListId\": %d}".formatted(listId);
	}

	private static final String PATCH_BODY = "{\"verdict\": \"BAD\", \"memo\": \"따가워요\"}";

	private static void expect401(ResultActions result) throws Exception {
		result.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").value(ErrorCode.UNAUTHORIZED.message()))
				.andExpect(jsonPath("$.traceId").value(matchesPattern("[0-9a-f]{8}")));
	}

	private static void expectCsrf403(ResultActions result) throws Exception {
		result.andExpect(status().isForbidden()).andExpect(jsonPath("$.code").value("CSRF_INVALID"));
	}

	// ---- GET /api/shelf ----

	@Test
	@DisplayName("GET: 로그인하지 않으면 401")
	void list_anonymous_401() throws Exception {
		expect401(mockMvc.perform(get("/api/shelf")));
	}

	@Test
	@DisplayName("GET: 내 항목만 ShelfItemResponse 모양으로 준다(B의 목록에 A의 항목이 없다)")
	void list_ownOnly() throws Exception {
		User a = newUser();
		User b = newUser();
		Product product = fixtures.product();
		IngredientList list = fixtures.list(product, 1, "정제수");
		Long aItem = shelfService.add(a.getId(), list.getId()).id();

		mockMvc.perform(get("/api/shelf").cookie(auth.accessTokenCookie(a)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(aItem))
				.andExpect(jsonPath("$[0].product.id").value(product.getId()))
				.andExpect(jsonPath("$[0].product.brand").value("브랜드"))
				.andExpect(jsonPath("$[0].product.name").value(product.getName()))
				.andExpect(jsonPath("$[0].product.representativeListId").value(list.getId()))
				.andExpect(jsonPath("$[0].ingredientListId").value(list.getId()))
				.andExpect(jsonPath("$[0].listIsRepresentative").value(true))
				.andExpect(jsonPath("$[0].verdict").value("UNRATED"))
				.andExpect(jsonPath("$[0].updatedAt").isString());

		mockMvc.perform(get("/api/shelf").cookie(auth.accessTokenCookie(b)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	// ---- POST /api/shelf ----

	@Test
	@DisplayName("POST: 로그인하지 않으면 401")
	void add_anonymous_401() throws Exception {
		expect401(mockMvc.perform(post("/api/shelf").with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(addBody(newList().getId()))));
	}

	@Test
	@DisplayName("POST: CSRF 토큰이 없으면 403이고 담지 않는다")
	void add_withoutCsrf_403() throws Exception {
		User user = newUser();

		expectCsrf403(mockMvc.perform(post("/api/shelf").cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content(addBody(newList().getId()))));
		assertThat(shelfItemRepository.findByUserIdOrderByUpdatedAtDesc(user.getId())).isEmpty();
	}

	@Test
	@DisplayName("POST: B가 A가 담은 버전을 담으면 B의 새 항목만 생기고 A의 항목은 그대로, 없는 버전은 404")
	void add_otherUsersList_createsOwnItemOnly() throws Exception {
		User a = newUser();
		User b = newUser();
		IngredientList list = newList();
		Long aItem = shelfService.add(a.getId(), list.getId()).id();
		shelfService.update(a.getId(), aItem, new UpdateShelfRequest(Verdict.GOOD, "A 메모", null, null));

		mockMvc.perform(post("/api/shelf").with(csrf()).cookie(auth.accessTokenCookie(b))
				.contentType(MediaType.APPLICATION_JSON).content(addBody(list.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.verdict").value("UNRATED"))
				.andExpect(jsonPath("$.memo").doesNotExist());

		mockMvc.perform(post("/api/shelf").with(csrf()).cookie(auth.accessTokenCookie(b))
				.contentType(MediaType.APPLICATION_JSON).content(addBody(Long.MAX_VALUE)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));

		ShelfItem aAfter = shelfItemRepository.findById(aItem).orElseThrow();
		assertThat(aAfter.getVerdict()).isEqualTo(Verdict.GOOD);
		assertThat(aAfter.getMemo()).isEqualTo("A 메모");
		assertThat(shelfItemRepository.findByUserIdOrderByUpdatedAtDesc(a.getId())).hasSize(1);
		assertThat(shelfItemRepository.findByUserIdOrderByUpdatedAtDesc(b.getId())).hasSize(1);
	}

	@Test
	@DisplayName("POST: 본인 요청은 201, 같은 버전을 또 담으면 409 SHELF_ITEM_DUPLICATE")
	void add_own_201_then409() throws Exception {
		User user = newUser();
		IngredientList list = newList();

		mockMvc.perform(post("/api/shelf").with(csrf()).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content(addBody(list.getId())))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.id").isNumber())
				.andExpect(jsonPath("$.ingredientListId").value(list.getId()))
				.andExpect(jsonPath("$.listIsRepresentative").value(true))
				.andExpect(jsonPath("$.verdict").value("UNRATED"));

		mockMvc.perform(post("/api/shelf").with(csrf()).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content(addBody(list.getId())))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("SHELF_ITEM_DUPLICATE"))
				.andExpect(jsonPath("$.message").value(ErrorCode.SHELF_ITEM_DUPLICATE.message()));
	}

	// ---- PATCH /api/shelf/{id} ----

	@Test
	@DisplayName("PATCH: 로그인하지 않으면 401")
	void update_anonymous_401() throws Exception {
		Long id = shelfItemOf(newUser());
		expect401(mockMvc.perform(patch("/api/shelf/{id}", id).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(PATCH_BODY)));
	}

	@Test
	@DisplayName("PATCH: CSRF 토큰이 없으면 403이고 바뀌지 않는다")
	void update_withoutCsrf_403() throws Exception {
		User user = newUser();
		Long id = shelfItemOf(user);

		expectCsrf403(mockMvc.perform(patch("/api/shelf/{id}", id).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content(PATCH_BODY)));
		assertThat(shelfItemRepository.findById(id).orElseThrow().getVerdict()).isEqualTo(Verdict.UNRATED);
	}

	@Test
	@DisplayName("PATCH: B가 A의 항목을 고치면 404이고 A의 항목은 그대로")
	void update_otherUsersItem_404() throws Exception {
		User a = newUser();
		User b = newUser();
		Long id = shelfItemOf(a);

		mockMvc.perform(patch("/api/shelf/{id}", id).with(csrf()).cookie(auth.accessTokenCookie(b))
				.contentType(MediaType.APPLICATION_JSON).content(PATCH_BODY))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));

		ShelfItem after = shelfItemRepository.findById(id).orElseThrow();
		assertThat(after.getVerdict()).isEqualTo(Verdict.UNRATED);
		assertThat(after.getMemo()).isNull();
	}

	@Test
	@DisplayName("PATCH: 본인 요청은 200 ShelfItemResponse, 종료일이 시작일보다 앞이면 400")
	void update_own_200() throws Exception {
		User user = newUser();
		Long id = shelfItemOf(user);

		mockMvc.perform(patch("/api/shelf/{id}", id).with(csrf()).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"verdict\": \"GOOD\", \"memo\": \"좋아요\", \"startedOn\": \"2026-10-01\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(id))
				.andExpect(jsonPath("$.verdict").value("GOOD"))
				.andExpect(jsonPath("$.memo").value("좋아요"))
				.andExpect(jsonPath("$.startedOn").value("2026-10-01"))
				.andExpect(jsonPath("$.product.id").isNumber());

		mockMvc.perform(patch("/api/shelf/{id}", id).with(csrf()).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content("{\"memo\": \"\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.verdict").value("GOOD"))
				.andExpect(jsonPath("$.memo").doesNotExist());

		mockMvc.perform(patch("/api/shelf/{id}", id).with(csrf()).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content("{\"endedOn\": \"2026-09-30\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		mockMvc.perform(patch("/api/shelf/{id}", id).with(csrf()).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content("{\"memo\": \"%s\"}".formatted("가".repeat(501))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	// ---- DELETE /api/shelf/{id} ----

	@Test
	@DisplayName("DELETE: 로그인하지 않으면 401")
	void remove_anonymous_401() throws Exception {
		Long id = shelfItemOf(newUser());
		expect401(mockMvc.perform(delete("/api/shelf/{id}", id).with(csrf())));
		assertThat(shelfItemRepository.findById(id)).isPresent();
	}

	@Test
	@DisplayName("DELETE: CSRF 토큰이 없으면 403이고 지우지 않는다")
	void remove_withoutCsrf_403() throws Exception {
		User user = newUser();
		Long id = shelfItemOf(user);

		expectCsrf403(mockMvc.perform(delete("/api/shelf/{id}", id).cookie(auth.accessTokenCookie(user))));
		assertThat(shelfItemRepository.findById(id)).isPresent();
	}

	@Test
	@DisplayName("DELETE: B가 A의 항목을 지우면 404이고 A의 항목은 그대로")
	void remove_otherUsersItem_404() throws Exception {
		User a = newUser();
		User b = newUser();
		Long id = shelfItemOf(a);

		mockMvc.perform(delete("/api/shelf/{id}", id).with(csrf()).cookie(auth.accessTokenCookie(b)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		assertThat(shelfItemRepository.findById(id)).isPresent();
	}

	@Test
	@DisplayName("DELETE: 본인 요청은 204이고 항목이 사라진다")
	void remove_own_204() throws Exception {
		User user = newUser();
		Long id = shelfItemOf(user);

		mockMvc.perform(delete("/api/shelf/{id}", id).with(csrf()).cookie(auth.accessTokenCookie(user)))
				.andExpect(status().isNoContent());
		assertThat(shelfItemRepository.findById(id)).isEmpty();
	}

	// ---- GET /api/shelf?ingredient= (성분으로 찾기) ----

	@Test
	@DisplayName("성분 검색: 로그인하지 않으면 401")
	void search_anonymous_401() throws Exception {
		expect401(mockMvc.perform(get("/api/shelf").param("ingredient", "정제수")));
	}

	@Test
	@DisplayName("성분 검색: 내 항목 중 일치한 것만 matchedIngredients와 함께, B에게는 A의 항목이 없다, 51자는 400")
	void search_own() throws Exception {
		User a = newUser();
		User b = newUser();
		IngredientList list = fixtures.list(fixtures.product(), 1, "정제수", "나이아신아마이드");
		Long aItem = shelfService.add(a.getId(), list.getId()).id();
		shelfService.add(a.getId(), newList().getId());

		mockMvc.perform(get("/api/shelf").param("ingredient", "나이아신").cookie(auth.accessTokenCookie(a)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(aItem))
				.andExpect(jsonPath("$[0].product.id").isNumber())
				.andExpect(jsonPath("$[0].matchedIngredients[0]").value("나이아신아마이드"));

		mockMvc.perform(get("/api/shelf").param("ingredient", "나이아신").cookie(auth.accessTokenCookie(b)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));

		mockMvc.perform(get("/api/shelf").param("ingredient", "가".repeat(51)).cookie(auth.accessTokenCookie(a)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		mockMvc.perform(get("/api/shelf").cookie(auth.accessTokenCookie(a)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(2))
				.andExpect(jsonPath("$[0].matchedIngredients").doesNotHaveJsonPath());
	}

	// ---- 개봉일·사용기한 (PATCH), GET /api/shelf/stats ----

	@Test
	@DisplayName("PATCH: 개봉일·사용기한을 저장하면 유통기한·상태를 돌려주고, 사용기한 0·61개월과 미래 개봉일은 400")
	void update_opening() throws Exception {
		User user = newUser();
		Long id = shelfItemOf(user);
		LocalDate today = LocalDate.now(ZoneId.of("Asia/Seoul"));
		LocalDate opened = today.minusMonths(3);

		mockMvc.perform(patch("/api/shelf/{id}", id).with(csrf()).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"openedOn\": \"%s\", \"paoMonths\": 12, \"startedOn\": \"%s\"}".formatted(opened, opened)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.openedOn").value(opened.toString()))
				.andExpect(jsonPath("$.paoMonths").value(12))
				.andExpect(jsonPath("$.expiresOn").value(opened.plusMonths(12).toString()))
				.andExpect(jsonPath("$.expiryStatus").value("OK"))
				.andExpect(jsonPath("$.usageDays").isNumber());

		for (String body : new String[] { "{\"paoMonths\": 0}", "{\"paoMonths\": 61}",
				"{\"openedOn\": \"%s\"}".formatted(today.plusDays(2)) }) {
			mockMvc.perform(patch("/api/shelf/{id}", id).with(csrf()).cookie(auth.accessTokenCookie(user))
					.contentType(MediaType.APPLICATION_JSON).content(body))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		}
		ShelfItem after = shelfItemRepository.findById(id).orElseThrow();
		assertThat(after.getPaoMonths()).isEqualTo(12);
		assertThat(after.getOpenedOn()).isEqualTo(opened);
	}

	@Test
	@DisplayName("PATCH: B가 A의 항목에 개봉일을 넣으면 404이고 A의 항목은 그대로")
	void update_opening_otherUser_404() throws Exception {
		User a = newUser();
		User b = newUser();
		Long id = shelfItemOf(a);

		mockMvc.perform(patch("/api/shelf/{id}", id).with(csrf()).cookie(auth.accessTokenCookie(b))
				.contentType(MediaType.APPLICATION_JSON).content("{\"openedOn\": \"2026-01-01\", \"paoMonths\": 6}"))
				.andExpect(status().isNotFound());
		ShelfItem after = shelfItemRepository.findById(id).orElseThrow();
		assertThat(after.getOpenedOn()).isNull();
		assertThat(after.getPaoMonths()).isNull();
	}

	@Test
	@DisplayName("stats: 로그인하지 않으면 401")
	void stats_anonymous_401() throws Exception {
		expect401(mockMvc.perform(get("/api/shelf/stats")));
	}

	@Test
	@DisplayName("stats: 내 항목만 센다(B의 통계에 A의 항목이 없다)")
	void stats_own() throws Exception {
		User a = newUser();
		User b = newUser();
		Long aItem = shelfItemOf(a);
		shelfService.update(a.getId(), aItem, new UpdateShelfRequest(Verdict.GOOD, null, null, null,
				LocalDate.now(ZoneId.of("Asia/Seoul")).minusMonths(13), 12));

		mockMvc.perform(get("/api/shelf/stats").cookie(auth.accessTokenCookie(a)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(1))
				.andExpect(jsonPath("$.inUse").value(1))
				.andExpect(jsonPath("$.finished").value(0))
				.andExpect(jsonPath("$.byCategory.SKINCARE").value(1))
				.andExpect(jsonPath("$.byCategory.MAKEUP").value(0))
				.andExpect(jsonPath("$.byVerdict.GOOD").value(1))
				.andExpect(jsonPath("$.byVerdict.UNRATED").value(0))
				.andExpect(jsonPath("$.expired[0].id").value(aItem))
				.andExpect(jsonPath("$.expired[0].expiryStatus").value("EXPIRED"))
				.andExpect(jsonPath("$.expiring.length()").value(0));

		mockMvc.perform(get("/api/shelf/stats").cookie(auth.accessTokenCookie(b)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.total").value(0))
				.andExpect(jsonPath("$.expired.length()").value(0));
	}
	// ---- 색상과 내 톤(no-ai-features 설계 E) ----

	private static final String SHADE_BODY = "{\"shadeName\": \"코랄 21호\", \"shadeHex\": \"#ff7f50\"}";

	private Long makeupItemOf(User user) {
		return shelfService.add(user.getId(), fixtures.list(fixtures.makeupProduct(), 1, "정제수").getId()).id();
	}

	@Test
	@DisplayName("PATCH 호수: 로그인하지 않으면 401")
	void shade_anonymous_401() throws Exception {
		expect401(mockMvc.perform(patch("/api/shelf/{id}", makeupItemOf(newUser())).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(SHADE_BODY)));
	}

	@Test
	@DisplayName("PATCH 호수: CSRF 토큰이 없으면 403이고 저장하지 않는다")
	void shade_withoutCsrf_403() throws Exception {
		User user = newUser();
		Long id = makeupItemOf(user);

		expectCsrf403(mockMvc.perform(patch("/api/shelf/{id}", id).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content(SHADE_BODY)));
		assertThat(shelfItemRepository.findById(id).orElseThrow().getShadeHex()).isNull();
	}

	@Test
	@DisplayName("PATCH 호수: B가 A의 항목에 호수를 넣으면 404이고 A의 항목은 그대로")
	void shade_otherUser_404() throws Exception {
		User a = newUser();
		User b = newUser();
		Long id = makeupItemOf(a);

		mockMvc.perform(patch("/api/shelf/{id}", id).with(csrf()).cookie(auth.accessTokenCookie(b))
				.contentType(MediaType.APPLICATION_JSON).content(SHADE_BODY))
				.andExpect(status().isNotFound());
		ShelfItem after = shelfItemRepository.findById(id).orElseThrow();
		assertThat(after.getShadeName()).isNull();
		assertThat(after.getShadeHex()).isNull();
	}

	@Test
	@DisplayName("PATCH 호수: 본인 요청은 200과 판정, #RRGGBB가 아니면 400")
	void shade_own_200() throws Exception {
		User user = newUser();
		Long id = makeupItemOf(user);
		skinProfileService.put(user.getId(), java.util.Set.of(), PersonalColor.SPRING_WARM);

		mockMvc.perform(patch("/api/shelf/{id}", id).with(csrf()).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content(SHADE_BODY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.shadeName").value("코랄 21호"))
				.andExpect(jsonPath("$.shadeHex").value("#FF7F50"))
				.andExpect(jsonPath("$.shadeTone").value("WARM"))
				.andExpect(jsonPath("$.shadeSeason").value("SPRING"))
				.andExpect(jsonPath("$.shadeMatchesMe").value(true));

		for (String bad : new String[] { "#12345", "ff7f50", "#GG0000", "#FF7F500" }) {
			mockMvc.perform(patch("/api/shelf/{id}", id).with(csrf()).cookie(auth.accessTokenCookie(user))
					.contentType(MediaType.APPLICATION_JSON).content("{\"shadeHex\": \"%s\"}".formatted(bad)))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		}
		mockMvc.perform(patch("/api/shelf/{id}", id).with(csrf()).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content("{\"shadeName\": \"%s\"}".formatted("가".repeat(51))))
				.andExpect(status().isBadRequest());
		assertThat(shelfItemRepository.findById(id).orElseThrow().getShadeHex()).isEqualTo("#FF7F50");
	}

	@Test
	@DisplayName("GET toneMatch: 로그인하지 않으면 401")
	void toneMatch_anonymous_401() throws Exception {
		expect401(mockMvc.perform(get("/api/shelf").param("toneMatch", "true")));
	}

	@Test
	@DisplayName("GET toneMatch: 퍼스널컬러가 없으면 400 PERSONAL_COLOR_REQUIRED")
	void toneMatch_noPersonalColor_400() throws Exception {
		User user = newUser();

		mockMvc.perform(get("/api/shelf").param("toneMatch", "true").cookie(auth.accessTokenCookie(user)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("PERSONAL_COLOR_REQUIRED"))
				.andExpect(jsonPath("$.message").value(ErrorCode.PERSONAL_COLOR_REQUIRED.message()));
	}

	@Test
	@DisplayName("GET toneMatch: 내 항목 중 톤이 맞는 색조만(B에게 A의 항목이 보이지 않는다)")
	void toneMatch_ownOnly() throws Exception {
		User a = newUser();
		User b = newUser();
		Long warm = makeupItemOf(a);
		Long cool = makeupItemOf(a);
		shelfService.update(a.getId(), warm, new UpdateShelfRequest(null, null, null, null, null, null, "코랄", "#FF7F50"));
		shelfService.update(a.getId(), cool, new UpdateShelfRequest(null, null, null, null, null, null, "핑크", "#FFB6C1"));
		skinProfileService.put(a.getId(), java.util.Set.of(), PersonalColor.AUTUMN_WARM);
		skinProfileService.put(b.getId(), java.util.Set.of(), PersonalColor.AUTUMN_WARM);

		mockMvc.perform(get("/api/shelf").param("toneMatch", "true").cookie(auth.accessTokenCookie(a)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(warm))
				.andExpect(jsonPath("$[0].shadeMatchesMe").value(true));

		mockMvc.perform(get("/api/shelf").param("toneMatch", "true").cookie(auth.accessTokenCookie(b)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(0));
	}

	// ---- 제품 종류(퍼스널 탭 설계 3절) ----

	private static final String TYPE_BODY = "{\"stepType\": \"SERUM\"}";

	@Test
	@DisplayName("PATCH 종류: 로그인하지 않으면 401")
	void type_anonymous_401() throws Exception {
		expect401(mockMvc.perform(patch("/api/shelf/{id}", shelfItemOf(newUser())).with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(TYPE_BODY)));
	}

	@Test
	@DisplayName("PATCH 종류: CSRF 토큰이 없으면 403이고 저장하지 않는다")
	void type_withoutCsrf_403() throws Exception {
		User user = newUser();
		Long id = shelfItemOf(user);

		expectCsrf403(mockMvc.perform(patch("/api/shelf/{id}", id).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content(TYPE_BODY)));
		assertThat(shelfItemRepository.findById(id).orElseThrow().getStepType()).isNull();
	}

	@Test
	@DisplayName("PATCH 종류: B가 A의 항목에 단계를 넣으면 404이고 A의 항목은 그대로")
	void type_otherUser_404() throws Exception {
		User a = newUser();
		User b = newUser();
		Long id = shelfItemOf(a);

		mockMvc.perform(patch("/api/shelf/{id}", id).with(csrf()).cookie(auth.accessTokenCookie(b))
				.contentType(MediaType.APPLICATION_JSON).content(TYPE_BODY))
				.andExpect(status().isNotFound());
		assertThat(shelfItemRepository.findById(id).orElseThrow().getStepType()).isNull();
	}

	@Test
	@DisplayName("PATCH 종류: 본인 요청은 200 {stepType}, 분류와 맞지 않으면 400 INVALID_ITEM_TYPE")
	void type_own_200() throws Exception {
		User user = newUser();
		Long id = shelfItemOf(user);

		mockMvc.perform(patch("/api/shelf/{id}", id).with(csrf()).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content(TYPE_BODY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.stepType").value("SERUM"))
				.andExpect(jsonPath("$.makeupType").doesNotExist());
		mockMvc.perform(patch("/api/shelf/{id}", id).with(csrf()).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content("{\"makeupType\": \"LIP\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_ITEM_TYPE"));
		mockMvc.perform(patch("/api/shelf/{id}", id).with(csrf()).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content("{\"stepType\": \"serum\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_ITEM_TYPE"));
		mockMvc.perform(patch("/api/shelf/{id}", id).with(csrf()).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content("{\"stepType\": \"%s\"}".formatted("S".repeat(21))))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		assertThat(shelfItemRepository.findById(id).orElseThrow().getStepType()).isEqualTo(StepType.SERUM);
	}
}
