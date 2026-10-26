package com.mycosmetic.controller;

import static org.hamcrest.Matchers.hasItem;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.mycosmetic.entity.User;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.ProductFixtures;
import com.mycosmetic.support.TestAuthHelper;

/** 제품명 점검·브랜드 목록 🌐(로그인 필요) — 조회만 하므로 보안 테스트 1·4번 */
class ProductNameCheckSecurityTest extends IntegrationTestSupport {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TestAuthHelper auth;

	@Autowired
	ProductFixtures fixtures;

	private static void expect401(ResultActions result) throws Exception {
		result.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").value(ErrorCode.UNAUTHORIZED.message()))
				.andExpect(jsonPath("$.traceId").value(matchesPattern("[0-9a-f]{8}")));
	}

	@Test
	@DisplayName("로그인하지 않으면 401")
	void anonymous_401() throws Exception {
		expect401(mockMvc.perform(get("/api/products/name-check").param("brand", "설화수").param("name", "크림")));
		expect401(mockMvc.perform(get("/api/products/brands")));
	}

	@Test
	@DisplayName("로그인하면 이름 점검 결과와 브랜드 목록, 브랜드·제품명이 비면 400")
	void loggedIn_200() throws Exception {
		User user = auth.createUser(TestAuthHelper.uniqueEmail());
		String brand = "보안점검브랜드" + ProductFixtures.unique();
		fixtures.product(ProductFixtures.newBarcode(), brand, "수분크림");

		mockMvc.perform(get("/api/products/name-check").cookie(auth.accessTokenCookie(user))
				.param("brand", brand).param("name", brand + " 수분크림 50ml"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("수분크림"))
				.andExpect(jsonPath("$.volume").value("50ml"))
				.andExpect(jsonPath("$.brandKnown").value(true))
				.andExpect(jsonPath("$.similar[0].sameName").value(true))
				.andExpect(jsonPath("$.similar[0].product.brand").value(brand));

		mockMvc.perform(get("/api/products/brands").cookie(auth.accessTokenCookie(user)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$", hasItem(brand)));

		mockMvc.perform(get("/api/products/name-check").cookie(auth.accessTokenCookie(user))
				.param("brand", brand).param("name", " "))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}
}
