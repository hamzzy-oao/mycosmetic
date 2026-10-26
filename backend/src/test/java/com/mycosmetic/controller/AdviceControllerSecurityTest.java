package com.mycosmetic.controller;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.mycosmetic.dto.shelf.UpdateShelfRequest;
import com.mycosmetic.entity.IngredientList;
import com.mycosmetic.entity.Product;
import com.mycosmetic.entity.User;
import com.mycosmetic.entity.Verdict;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.service.shelf.ShelfService;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.ProductFixtures;
import com.mycosmetic.support.TestAuthHelper;

/** POST /api/advice 🔒 — 401, CSRF 403, 남의 화장대 미사용, 정상 */
class AdviceControllerSecurityTest extends IntegrationTestSupport {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TestAuthHelper auth;

	@Autowired
	ProductFixtures fixtures;

	@Autowired
	ShelfService shelfService;

	private User newUser() {
		return auth.createUser(TestAuthHelper.uniqueEmail());
	}

	@Test
	@DisplayName("로그인하지 않으면 401")
	void anonymous_401() throws Exception {
		mockMvc.perform(post("/api/advice").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"ingredientsText\":\"정제수\"}"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").value(ErrorCode.UNAUTHORIZED.message()))
				.andExpect(jsonPath("$.traceId").value(matchesPattern("[0-9a-f]{8}")));
	}

	@Test
	@DisplayName("CSRF 토큰이 없으면 403")
	void withoutCsrf_403() throws Exception {
		User user = newUser();
		mockMvc.perform(post("/api/advice").cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content("{\"ingredientsText\":\"정제수\"}"))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CSRF_INVALID"));
	}

	@Test
	@DisplayName("A의 화장대 기록은 B의 판단에 쓰이지 않는다")
	void otherUsersShelf_notUsed() throws Exception {
		User a = newUser();
		User b = newUser();
		Product product = fixtures.product();
		IngredientList list = fixtures.list(product, 1, "정제수", "레티놀");
		Long aItem = shelfService.add(a.getId(), list.getId()).id();
		shelfService.update(a.getId(), aItem, new UpdateShelfRequest(Verdict.BAD, null, null, null));

		mockMvc.perform(post("/api/advice").with(csrf()).cookie(auth.accessTokenCookie(b))
				.contentType(MediaType.APPLICATION_JSON).content("{\"productId\":" + product.getId() + "}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.alreadyOnShelf").value(false))
				.andExpect(jsonPath("$.relatedItems.length()").value(0))
				.andExpect(jsonPath("$.reasons[0].type").value("NEED_MORE_RECORDS"));
	}

	@Test
	@DisplayName("사진 결과(jobId)로는 판단하지 않는다: jobId만 보내면 400")
	void jobId_notSupported() throws Exception {
		User user = newUser();

		mockMvc.perform(post("/api/advice").with(csrf()).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content("{\"jobId\":1}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	@DisplayName("본인 요청은 200과 판단 결과(제품·붙여넣기), 형식이 틀리면 400")
	void own_200() throws Exception {
		User user = newUser();
		var cookie = auth.accessTokenCookie(user);
		Product product = fixtures.product();
		IngredientList rep = fixtures.list(product, 1, "정제수", "레티놀");

		mockMvc.perform(post("/api/advice").with(csrf()).cookie(cookie).contentType(MediaType.APPLICATION_JSON)
				.content("{\"productId\":" + product.getId() + "}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.verdict").value("UNKNOWN"))
				.andExpect(jsonPath("$.reasons[0].type").value("NEED_MORE_RECORDS"))
				.andExpect(jsonPath("$.reasons[0].level").value("INFO"))
				.andExpect(jsonPath("$.reasons[0].ingredients").isArray())
				.andExpect(jsonPath("$.reasons[0].relatedShelfItemIds").isArray())
				.andExpect(jsonPath("$.matchedCount").value(0))
				.andExpect(jsonPath("$.unmatchedCount").value(2))
				.andExpect(jsonPath("$.ingredients[1]").value("레티놀"))
				.andExpect(jsonPath("$.product.id").value(product.getId()))
				.andExpect(jsonPath("$.product.representativeListId").value(rep.getId()))
				.andExpect(jsonPath("$.alreadyOnShelf").value(false))
				.andExpect(jsonPath("$.relatedItems").isArray());

		mockMvc.perform(post("/api/advice").with(csrf()).cookie(cookie).contentType(MediaType.APPLICATION_JSON)
				.content("{\"ingredientsText\":\"정제수, 향료\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.ingredients.length()").value(2));

		mockMvc.perform(post("/api/advice").with(csrf()).cookie(cookie).contentType(MediaType.APPLICATION_JSON)
				.content("{\"ingredientsText\":\"" + "a".repeat(3001) + "\"}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));

		mockMvc.perform(post("/api/advice").with(csrf()).cookie(cookie).contentType(MediaType.APPLICATION_JSON)
				.content("{}"))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}
}
