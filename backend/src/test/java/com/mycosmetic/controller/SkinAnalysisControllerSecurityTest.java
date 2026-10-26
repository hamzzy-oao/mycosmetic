package com.mycosmetic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.mycosmetic.dto.shelf.UpdateShelfRequest;
import com.mycosmetic.dto.skin.SkinAnalysisRequest;
import com.mycosmetic.entity.SkinConcern;
import com.mycosmetic.entity.SkinType;
import com.mycosmetic.entity.User;
import com.mycosmetic.entity.Verdict;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.repository.SkinAnalysisRepository;
import com.mycosmetic.repository.SkinProfileRepository;
import com.mycosmetic.service.shelf.ShelfService;
import com.mycosmetic.service.skin.SelfType;
import com.mycosmetic.service.skin.SkinAnalysisService;
import com.mycosmetic.service.skin.SkinProfileService;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.ProductFixtures;
import com.mycosmetic.support.TestAuthHelper;

/** 피부 분석 API — POST 보안 테스트 4종, history 1·3·4번, questions 공개(퍼스널 탭 설계 1절) */
class SkinAnalysisControllerSecurityTest extends IntegrationTestSupport {

	static final String BODY = "{\"selfType\": \"COMBINATION\", \"concerns\": [\"SENSITIVITY\", \"PORES\"]}";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TestAuthHelper auth;

	@Autowired
	SkinAnalysisService skinAnalysisService;

	@Autowired
	SkinProfileService skinProfileService;

	@Autowired
	SkinProfileRepository skinProfileRepository;

	@Autowired
	SkinAnalysisRepository skinAnalysisRepository;

	@Autowired
	ShelfService shelfService;

	@Autowired
	ProductFixtures fixtures;

	private User newUser() {
		return auth.createUser(TestAuthHelper.uniqueEmail());
	}

	private void shelfItem(User user, Verdict verdict, Object... lines) {
		Long id = shelfService.add(user.getId(), fixtures.list(fixtures.product(), 1, lines).getId()).id();
		shelfService.update(user.getId(), id, new UpdateShelfRequest(verdict, null, null, null));
	}

	private static void expect401(ResultActions result) throws Exception {
		result.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").value(ErrorCode.UNAUTHORIZED.message()))
				.andExpect(jsonPath("$.traceId").value(matchesPattern("[0-9a-f]{8}")));
	}

	@Test
	@DisplayName("POST·history: 로그인하지 않으면 401")
	void anonymous_401() throws Exception {
		expect401(mockMvc.perform(post("/api/skin-analysis").with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(BODY)));
		expect401(mockMvc.perform(get("/api/skin-analysis/history")));
	}

	@Test
	@DisplayName("POST: CSRF 토큰이 없으면 403이고 저장하지 않는다")
	void noCsrf_403() throws Exception {
		User user = newUser();

		mockMvc.perform(post("/api/skin-analysis").cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CSRF_INVALID"));
		assertThat(skinProfileRepository.findById(user.getId())).isEmpty();
		assertThat(skinAnalysisRepository.findTop10ByUserIdOrderByCreatedAtDescIdDesc(user.getId())).isEmpty();
	}

	@Test
	@DisplayName("B가 분석해도 A의 피부 정보·이력은 그대로이고, B의 결과·이력에 A의 화장대·이력이 섞이지 않는다")
	void otherUser_doesNotTouchA() throws Exception {
		User a = newUser();
		User b = newUser();
		// A의 화장대에만 안맞아요 공통 성분이 있다. B의 결과에 후보 성분·민감성 제안이 나오면 A의 기록이 섞인 것
		String x = "A만의자극" + ProductFixtures.unique();
		shelfItem(a, Verdict.BAD, x, "정제수");
		shelfItem(a, Verdict.BAD, x);
		skinAnalysisService.analyze(a.getId(), new SkinAnalysisRequest(SelfType.DRY, null, List.of(SkinConcern.WRINKLES)));

		mockMvc.perform(post("/api/skin-analysis").with(csrf()).cookie(auth.accessTokenCookie(b))
				.contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.candidateIngredients.length()").value(0))
				.andExpect(jsonPath("$.suggestSensitive").value(false));
		mockMvc.perform(get("/api/skin-analysis/history").cookie(auth.accessTokenCookie(b)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(1))
				.andExpect(jsonPath("$.items[0].baseType").value("COMBINATION"));

		var profileA = skinProfileRepository.findById(a.getId()).orElseThrow();
		assertThat(profileA.getSkinTypes()).containsExactly(SkinType.DRY);
		assertThat(profileA.getConcerns()).containsExactly(SkinConcern.WRINKLES);
		assertThat(skinAnalysisRepository.findTop10ByUserIdOrderByCreatedAtDescIdDesc(a.getId()))
				.singleElement().satisfies(s -> assertThat(s.getResultTypes()).containsExactly(SkinType.DRY));
	}

	@Test
	@DisplayName("POST: 본인 요청은 200 {baseType, tags, concerns, reasons}, 잘못된 답변은 400")
	void own_200() throws Exception {
		User user = newUser();

		mockMvc.perform(post("/api/skin-analysis").with(csrf()).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.baseType").value("COMBINATION"))
				.andExpect(jsonPath("$.tags[0]").value("SENSITIVE"))
				.andExpect(jsonPath("$.concerns.length()").value(2))
				.andExpect(jsonPath("$.reasons.length()").isNumber())
				.andExpect(jsonPath("$.suggestSensitive").value(false))
				.andExpect(jsonPath("$.ruleVersion").value("skin-v1"));

		mockMvc.perform(post("/api/skin-analysis").with(csrf()).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content("{\"selfType\": \"UNSURE\", \"concerns\": []}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_SKIN_ANSWERS"));
		mockMvc.perform(post("/api/skin-analysis").with(csrf()).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"selfType\": \"DRY\", \"concerns\": [\"PORES\", \"ACNE\", \"DRYNESS\", \"WRINKLES\"]}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_SKIN_ANSWERS"));

		assertThat(skinProfileRepository.findById(user.getId()).orElseThrow().getConcerns())
				.containsExactly(SkinConcern.PORES, SkinConcern.SENSITIVITY);
	}

	@Test
	@DisplayName("GET /api/skin-profile에 고민도 함께 나온다")
	void profile_includesConcerns() throws Exception {
		User user = newUser();
		skinAnalysisService.analyze(user.getId(), new SkinAnalysisRequest(SelfType.OILY, null,
				List.of(SkinConcern.PORES)));

		mockMvc.perform(get("/api/skin-profile").cookie(auth.accessTokenCookie(user)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.skinTypes[0]").value("OILY"))
				.andExpect(jsonPath("$.concerns[0]").value("PORES"));
		skinProfileService.put(user.getId(), Set.of(SkinType.DRY));
		mockMvc.perform(get("/api/skin-profile").cookie(auth.accessTokenCookie(user)))
				.andExpect(jsonPath("$.concerns[0]").value("PORES"));
	}

	@Test
	@DisplayName("알 수 없는 값은 400 INVALID_REQUEST이고 저장하지 않는다")
	void unknownEnum_400() throws Exception {
		User user = newUser();

		mockMvc.perform(post("/api/skin-analysis").with(csrf()).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content("{\"selfType\": \"HACK\", \"concerns\": []}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		assertThat(skinAnalysisRepository.findTop10ByUserIdOrderByCreatedAtDescIdDesc(user.getId())).isEmpty();
	}

	@Test
	@DisplayName("공개는 GET questions 하나뿐: POST questions, GET /api/skin-analysis는 로그인 없이 401")
	void onlyQuestionsIsPublic() throws Exception {
		expect401(mockMvc.perform(post("/api/skin-analysis/questions").with(csrf())));
		expect401(mockMvc.perform(get("/api/skin-analysis")));
	}

	@Test
	@DisplayName("questions: 로그인 없이 200")
	void questions_public() throws Exception {
		mockMvc.perform(get("/api/skin-analysis/questions"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ruleVersion").value("skin-v1"))
				.andExpect(jsonPath("$.questions.length()").value(3));
	}
}
