package com.mycosmetic.controller;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.mycosmetic.entity.Ingredient;
import com.mycosmetic.entity.IngredientList;
import com.mycosmetic.entity.Product;
import com.mycosmetic.entity.User;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.ProductFixtures;
import com.mycosmetic.support.TestAuthHelper;

import jakarta.servlet.http.Cookie;

/** 제품 조회 API(🌐 데이터, 로그인 필요) — 보안 테스트 1번(401)과 4번(정상) */
class ProductQuerySecurityTest extends IntegrationTestSupport {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TestAuthHelper auth;

	@Autowired
	ProductFixtures fixtures;

	private Cookie login() {
		User user = auth.createUser(TestAuthHelper.uniqueEmail());
		return auth.accessTokenCookie(user);
	}

	private static void expect401(ResultActions result) throws Exception {
		result.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").isString())
				.andExpect(jsonPath("$.traceId").value(matchesPattern("[0-9a-f]{8}")));
	}

	@Test
	@DisplayName("로그인하지 않으면 검색·상세·특정 버전·diff 모두 401")
	void anonymous_returns401() throws Exception {
		Product product = fixtures.product();
		IngredientList list = fixtures.list(product, 1, "정제수");

		expect401(mockMvc.perform(get("/api/products").param("q", "토너")));
		expect401(mockMvc.perform(get("/api/products/{id}", product.getId())));
		expect401(mockMvc.perform(get("/api/products/{id}/lists/{listId}", product.getId(), list.getId())));
		expect401(mockMvc.perform(get("/api/products/{id}/lists/diff", product.getId())
				.param("from", list.getId().toString()).param("to", list.getId().toString())));
	}

	@Test
	@DisplayName("이름 검색은 200 목록, q가 없으면 400")
	void search_ok_andMissingParams400() throws Exception {
		Cookie cookie = login();
		String token = ProductFixtures.unique();
		Product product = fixtures.product(null, "브랜드", "토너" + token);

		mockMvc.perform(get("/api/products").param("q", token).cookie(cookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].id").value(product.getId()))
				.andExpect(jsonPath("$[0].barcode").doesNotExist())
				.andExpect(jsonPath("$[0].representativeListId").doesNotExist());

		mockMvc.perform(get("/api/products").cookie(cookie))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	@DisplayName("검색어는 100자까지: 100자는 200, 101자는 400 INVALID_REQUEST")
	void search_queryLongerThan100_400() throws Exception {
		Cookie cookie = login();

		mockMvc.perform(get("/api/products").param("q", "가".repeat(100)).cookie(cookie))
				.andExpect(status().isOk());
		mockMvc.perform(get("/api/products").param("q", "가".repeat(101)).cookie(cookie))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	@Test
	@DisplayName("상세는 200 {product, representative, otherLists}")
	void detail_ok() throws Exception {
		Cookie cookie = login();
		Product product = fixtures.product();
		Ingredient water = fixtures.ingredient("정제수");
		IngredientList other = fixtures.list(product, 1, "향료");
		IngredientList rep = fixtures.list(product, 2, water, "향료");

		mockMvc.perform(get("/api/products/{id}", product.getId()).cookie(cookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.product.id").value(product.getId()))
				.andExpect(jsonPath("$.product.category").value("SKINCARE"))
				.andExpect(jsonPath("$.representative.id").value(rep.getId()))
				.andExpect(jsonPath("$.representative.status").value("VERIFIED"))
				.andExpect(jsonPath("$.representative.confirmCount").value(2))
				.andExpect(jsonPath("$.representative.items[0].position").value(1))
				.andExpect(jsonPath("$.representative.items[0].ingredientId").value(water.getId()))
				.andExpect(jsonPath("$.representative.items[0].name").value(water.getStandardName()))
				.andExpect(jsonPath("$.representative.items[0].matchType").value("EXACT"))
				.andExpect(jsonPath("$.representative.items[1].name").value("향료"))
				.andExpect(jsonPath("$.representative.items[1].matchType").value("UNMATCHED"))
				.andExpect(jsonPath("$.otherLists[0].id").value(other.getId()))
				.andExpect(jsonPath("$.otherLists[0].status").value("PENDING"))
				.andExpect(jsonPath("$.otherLists[0].confirmCount").value(1))
				.andExpect(jsonPath("$.otherLists[0].createdAt").isString());

		mockMvc.perform(get("/api/products/{id}", Long.MAX_VALUE).cookie(cookie))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}

	@Test
	@DisplayName("diff는 200 {added, removed, orderChanged}, from·to가 없으면 400")
	void diff_ok() throws Exception {
		Cookie cookie = login();
		Product product = fixtures.product();
		IngredientList from = fixtures.list(product, 1, "정제수", "향료");
		IngredientList to = fixtures.list(product, 1, "정제수", "글리세린");

		mockMvc.perform(get("/api/products/{id}/lists/diff", product.getId()).cookie(cookie)
				.param("from", from.getId().toString()).param("to", to.getId().toString()))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.added[0]").value("글리세린"))
				.andExpect(jsonPath("$.removed[0]").value("향료"))
				.andExpect(jsonPath("$.orderChanged").value(false));

		mockMvc.perform(get("/api/products/{id}/lists/diff", product.getId()).cookie(cookie)
				.param("from", from.getId().toString()))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}

	/** 성분 버전은 공유 데이터(🌐)라 남의 데이터 거절 테스트(3번)는 없다. 다른 제품의 버전만 404로 막는다 */
	@Test
	@DisplayName("특정 버전은 200 ListView, 다른 제품의 버전이면 404")
	void list_ok_andOtherProduct404() throws Exception {
		Cookie cookie = login();
		Product product = fixtures.product();
		Ingredient water = fixtures.ingredient("정제수");
		IngredientList mine = fixtures.list(product, 1, water, "향료");
		fixtures.list(product, 2, "향료");
		IngredientList otherProductList = fixtures.list(fixtures.product(), 1, "정제수");

		mockMvc.perform(get("/api/products/{id}/lists/{listId}", product.getId(), mine.getId()).cookie(cookie))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(mine.getId()))
				.andExpect(jsonPath("$.status").value("PENDING"))
				.andExpect(jsonPath("$.confirmCount").value(1))
				.andExpect(jsonPath("$.items[0].position").value(1))
				.andExpect(jsonPath("$.items[0].ingredientId").value(water.getId()))
				.andExpect(jsonPath("$.items[0].name").value(water.getStandardName()))
				.andExpect(jsonPath("$.items[0].matchType").value("EXACT"))
				.andExpect(jsonPath("$.items[1].name").value("향료"))
				.andExpect(jsonPath("$.items[1].matchType").value("UNMATCHED"));

		mockMvc.perform(get("/api/products/{id}/lists/{listId}", product.getId(), otherProductList.getId())
				.cookie(cookie))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
	}
}
