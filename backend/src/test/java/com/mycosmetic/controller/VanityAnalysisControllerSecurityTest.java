package com.mycosmetic.controller;

import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.mycosmetic.entity.Product;
import com.mycosmetic.entity.User;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.service.shelf.ShelfService;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.ProductFixtures;
import com.mycosmetic.support.TestAuthHelper;

/** 화장대 분석 API — GET이라 1·3·4번(CSRF 대상 아님)과, 상태를 바꾸는 메서드가 열려 있지 않은지 */
class VanityAnalysisControllerSecurityTest extends IntegrationTestSupport {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TestAuthHelper auth;

	@Autowired
	ShelfService shelfService;

	@Autowired
	ProductFixtures fixtures;

	private User newUser() {
		return auth.createUser(TestAuthHelper.uniqueEmail());
	}

	private Long add(User user, String name) {
		Product product = fixtures.product(ProductFixtures.newBarcode(), "브랜드", name + " " + ProductFixtures.unique());
		return shelfService.add(user.getId(), fixtures.list(product, 1, "정제수").getId()).id();
	}

	@Test
	@DisplayName("로그인하지 않으면 401")
	void anonymous_401() throws Exception {
		mockMvc.perform(get("/api/vanity/analysis"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").value(ErrorCode.UNAUTHORIZED.message()))
				.andExpect(jsonPath("$.traceId").value(matchesPattern("[0-9a-f]{8}")));
	}

	@Test
	@DisplayName("B의 분석에는 A의 화장대 항목이 나오지 않는다")
	void otherUser_notIncluded() throws Exception {
		User a = newUser();
		User b = newUser();
		add(a, "A의 크림");
		add(a, "A의 멀티 밤"); // 이름으로 단계를 알 수 없어 A의 needsType에만 들어가야 한다

		mockMvc.perform(get("/api/vanity/analysis").cookie(auth.accessTokenCookie(b)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.items.length()").value(0))
				.andExpect(jsonPath("$.routineDraft.morning.length()").value(0))
				.andExpect(jsonPath("$.gaps.length()").value(3))
				.andExpect(jsonPath("$.needsType.length()").value(0))
				.andExpect(jsonPath("$.alternates.length()").value(0));
	}

	@Test
	@DisplayName("본인 요청은 200과 초안·빈 자리·항목")
	void own_200() throws Exception {
		User user = newUser();
		Long cream = add(user, "세라 크림");

		mockMvc.perform(get("/api/vanity/analysis").cookie(auth.accessTokenCookie(user)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.routineDraft.morning[0].shelfItemId").value(cream))
				.andExpect(jsonPath("$.routineDraft.morning[0].stepType").value("CREAM"))
				.andExpect(jsonPath("$.items[0].name").isString())
				.andExpect(jsonPath("$.gaps[0].role").value("CLEANSER"))
				.andExpect(jsonPath("$.needsType.length()").value(0));
	}

	@Test
	@DisplayName("POST는 열려 있지 않다(로그인해도 405 또는 404, 저장하는 API가 아니다)")
	void post_notAllowed() throws Exception {
		User user = newUser();

		mockMvc.perform(post("/api/vanity/analysis").with(csrf()).cookie(auth.accessTokenCookie(user)))
				.andExpect(status().is4xxClientError());
	}
}
