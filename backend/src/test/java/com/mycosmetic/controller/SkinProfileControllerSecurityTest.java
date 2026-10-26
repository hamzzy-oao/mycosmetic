package com.mycosmetic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.mycosmetic.entity.SkinType;
import com.mycosmetic.entity.User;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.repository.SkinProfileRepository;
import com.mycosmetic.service.skin.SkinProfileService;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.TestAuthHelper;

/** 피부 상태 API — PUT 보안 테스트 4종, GET 1·4번 */
class SkinProfileControllerSecurityTest extends IntegrationTestSupport {

	static final String BODY = "{\"skinTypes\": [\"DRY\", \"SENSITIVE\"]}";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TestAuthHelper auth;

	@Autowired
	SkinProfileService skinProfileService;

	@Autowired
	SkinProfileRepository skinProfileRepository;

	private User newUser() {
		return auth.createUser(TestAuthHelper.uniqueEmail());
	}

	private static void expect401(ResultActions result) throws Exception {
		result.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").value(ErrorCode.UNAUTHORIZED.message()))
				.andExpect(jsonPath("$.traceId").value(matchesPattern("[0-9a-f]{8}")));
	}

	@Test
	@DisplayName("GET·PUT: 로그인하지 않으면 401")
	void anonymous_401() throws Exception {
		expect401(mockMvc.perform(get("/api/skin-profile")));
		expect401(mockMvc.perform(put("/api/skin-profile").with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(BODY)));
	}

	@Test
	@DisplayName("GET: 저장한 적이 없으면 {skinTypes: []}, 저장했으면 내 값")
	void get_own() throws Exception {
		User user = newUser();

		mockMvc.perform(get("/api/skin-profile").cookie(auth.accessTokenCookie(user)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.skinTypes.length()").value(0));

		skinProfileService.put(user.getId(), Set.of(SkinType.OILY));
		mockMvc.perform(get("/api/skin-profile").cookie(auth.accessTokenCookie(user)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.skinTypes[0]").value("OILY"));
	}

	@Test
	@DisplayName("PUT: CSRF 토큰이 없으면 403이고 저장하지 않는다")
	void put_withoutCsrf_403() throws Exception {
		User user = newUser();

		mockMvc.perform(put("/api/skin-profile").cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CSRF_INVALID"));
		assertThat(skinProfileRepository.findById(user.getId())).isEmpty();
	}

	@Test
	@DisplayName("PUT: B가 PUT해도 A의 피부 상태는 그대로")
	void put_byOtherUser_doesNotTouchA() throws Exception {
		User a = newUser();
		User b = newUser();
		skinProfileService.put(a.getId(), Set.of(SkinType.OILY, SkinType.ACNE_PRONE));

		mockMvc.perform(put("/api/skin-profile").with(csrf()).cookie(auth.accessTokenCookie(b))
				.contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isOk());

		assertThat(skinProfileRepository.findById(a.getId()).orElseThrow().getSkinTypes())
				.containsExactly(SkinType.ACNE_PRONE, SkinType.OILY);
		assertThat(skinProfileRepository.findById(b.getId()).orElseThrow().getSkinTypes())
				.containsExactly(SkinType.SENSITIVE, SkinType.DRY);
	}

	@Test
	@DisplayName("PUT: 본인 요청은 200 {skinTypes}, 모르는 값이나 skinTypes가 없으면 400")
	void put_own_200() throws Exception {
		User user = newUser();

		mockMvc.perform(put("/api/skin-profile").with(csrf()).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.skinTypes.length()").value(2))
				.andExpect(jsonPath("$.skinTypes[0]").value("SENSITIVE"))
				.andExpect(jsonPath("$.skinTypes[1]").value("DRY"));

		mockMvc.perform(put("/api/skin-profile").with(csrf()).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content("{\"skinTypes\": [\"SHINY\"]}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		mockMvc.perform(put("/api/skin-profile").with(csrf()).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		// 기본 타입 없이 태그만, 기본 타입 2개는 INVALID_SKIN_TYPES(퍼스널 탭 설계 1절)
		mockMvc.perform(put("/api/skin-profile").with(csrf()).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content("{\"skinTypes\": [\"SENSITIVE\"]}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_SKIN_TYPES"));
		mockMvc.perform(put("/api/skin-profile").with(csrf()).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content("{\"skinTypes\": [\"DRY\", \"OILY\"]}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_SKIN_TYPES"));

		assertThat(skinProfileRepository.findById(user.getId()).orElseThrow().getSkinTypes())
				.containsExactly(SkinType.SENSITIVE, SkinType.DRY);
	}
	@Test
	@DisplayName("PUT·GET: 퍼스널컬러를 저장하고 돌려준다. 빼고 보내면 지우고, 모르는 값은 400")
	void personalColor_own() throws Exception {
		User user = newUser();

		mockMvc.perform(put("/api/skin-profile").with(csrf()).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"skinTypes\": [\"DRY\"], \"personalColor\": \"WINTER_COOL\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.personalColor").value("WINTER_COOL"));
		mockMvc.perform(get("/api/skin-profile").cookie(auth.accessTokenCookie(user)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.skinTypes[0]").value("DRY"))
				.andExpect(jsonPath("$.personalColor").value("WINTER_COOL"));

		mockMvc.perform(put("/api/skin-profile").with(csrf()).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"skinTypes\": [], \"personalColor\": \"WARM\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		assertThat(skinProfileRepository.findById(user.getId()).orElseThrow().getPersonalColor())
				.isEqualTo(com.mycosmetic.entity.PersonalColor.WINTER_COOL);

		mockMvc.perform(put("/api/skin-profile").with(csrf()).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content("{\"skinTypes\": [\"DRY\"]}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.personalColor").doesNotExist());
		assertThat(skinProfileRepository.findById(user.getId()).orElseThrow().getPersonalColor()).isNull();
	}
}
