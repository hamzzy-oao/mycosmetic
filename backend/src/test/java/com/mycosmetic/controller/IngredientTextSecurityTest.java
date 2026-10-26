package com.mycosmetic.controller;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import com.mycosmetic.entity.Ingredient;
import com.mycosmetic.entity.IngredientAlias;
import com.mycosmetic.entity.User;
import com.mycosmetic.repository.IngredientAliasRepository;
import com.mycosmetic.repository.IngredientRepository;
import com.mycosmetic.service.ingredient.IngredientNameNormalizer;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.TestAuthHelper;

/**
 * POST /api/ingredient-text/match 🔒 — 401, CSRF 403, 빈 글 400, 정상(구역별 사전 매칭).
 * 사용자 데이터를 읽거나 저장하지 않는 API라 "남의 데이터" 대신 입력 검증을 확인한다.
 */
class IngredientTextSecurityTest extends IntegrationTestSupport {

	static final String TEXT = "[크림] 정제수, 1,2-헥산다이올\\n[페어] 정제수, 글리세린";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TestAuthHelper auth;

	@Autowired
	IngredientRepository ingredientRepository;

	@Autowired
	IngredientAliasRepository aliasRepository;

	@Autowired
	JdbcTemplate jdbcTemplate;

	Ingredient water;

	@AfterEach
	void cleanUp() {
		if (water != null) {
			jdbcTemplate.update("DELETE FROM ingredient_aliases WHERE ingredient_id = ?", water.getId());
			jdbcTemplate.update("DELETE FROM ingredients WHERE id = ?", water.getId());
		}
	}

	private static String body(String text) {
		return "{\"text\":\"" + text + "\"}";
	}

	@Test
	@DisplayName("로그인하지 않으면 401")
	void anonymous_401() throws Exception {
		mockMvc.perform(post("/api/ingredient-text/match").with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(body(TEXT)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.traceId").value(matchesPattern("[0-9a-f]{8}")));
	}

	@Test
	@DisplayName("CSRF 토큰이 없으면 403")
	void withoutCsrf_403() throws Exception {
		User user = auth.createUser(TestAuthHelper.uniqueEmail());

		mockMvc.perform(post("/api/ingredient-text/match").cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content(body(TEXT)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CSRF_INVALID"));
	}

	@Test
	@DisplayName("빈 글은 400")
	void blank_400() throws Exception {
		User user = auth.createUser(TestAuthHelper.uniqueEmail());

		mockMvc.perform(post("/api/ingredient-text/match").with(csrf()).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content(body("  ")))
				.andExpect(status().isBadRequest());
	}

	@Test
	@DisplayName("본인 요청은 구역별로 나눠 사전과 맞춘 결과")
	void own_200() throws Exception {
		water = ingredientRepository.save(Ingredient.create("정제수", null, null));
		aliasRepository.save(IngredientAlias.create(water, "정제수", IngredientNameNormalizer.normalize("정제수").value()));
		User user = auth.createUser(TestAuthHelper.uniqueEmail());

		mockMvc.perform(post("/api/ingredient-text/match").with(csrf()).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content(body(TEXT)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.sections.length()").value(2))
				.andExpect(jsonPath("$.sections[0].label").value("크림"))
				.andExpect(jsonPath("$.sections[0].items[0].type").value("EXACT"))
				.andExpect(jsonPath("$.sections[0].items[0].ingredientId").value(water.getId()))
				.andExpect(jsonPath("$.sections[0].items[1].rawText").value("1,2-헥산다이올"))
				.andExpect(jsonPath("$.sections[1].label").value("페어"));
	}
}
