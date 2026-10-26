package com.mycosmetic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.web.servlet.MockMvc;

import com.mycosmetic.entity.IngredientList;
import com.mycosmetic.entity.Product;
import com.mycosmetic.entity.ShelfItem;
import com.mycosmetic.entity.User;
import com.mycosmetic.entity.Verdict;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.repository.ShelfItemRepository;
import com.mycosmetic.service.shelf.ShelfService;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.ProductFixtures;
import com.mycosmetic.support.TestAuthHelper;

/** GET /api/compare 보안 테스트 — GET이라 CSRF 테스트는 없다(401, 남의 항목 404, 정상) */
class CompareControllerSecurityTest extends IntegrationTestSupport {

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

	private User newUser() {
		return auth.createUser(TestAuthHelper.uniqueEmail());
	}

	private Long shelfItemOf(User user, Object... lines) {
		return shelfService.add(user.getId(), fixtures.list(fixtures.product(), 1, lines).getId()).id();
	}

	@Test
	@DisplayName("로그인하지 않으면 401")
	void anonymous_401() throws Exception {
		Long a = shelfItemOf(newUser(), "정제수");
		mockMvc.perform(get("/api/compare").param("a", a.toString()).param("b", a.toString()))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").value(ErrorCode.UNAUTHORIZED.message()))
				.andExpect(jsonPath("$.traceId").value(matchesPattern("[0-9a-f]{8}")));
	}

	@Test
	@DisplayName("B가 A의 화장대 항목으로 비교하면 404이고 A의 항목은 그대로")
	void otherUsersItem_404() throws Exception {
		User a = newUser();
		User b = newUser();
		Long aItem = shelfItemOf(a, "정제수");
		Long bItem = shelfItemOf(b, "정제수");

		mockMvc.perform(get("/api/compare").param("a", bItem.toString()).param("b", aItem.toString())
				.cookie(auth.accessTokenCookie(b)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));

		ShelfItem after = shelfItemRepository.findById(aItem).orElseThrow();
		assertThat(after.getVerdict()).isEqualTo(Verdict.UNRATED);
		assertThat(shelfItemRepository.findByUserIdOrderByUpdatedAtDesc(a.getId())).hasSize(1);
	}

	@Test
	@DisplayName("본인 요청은 200과 비교 결과, 한쪽이 빠지면 400")
	void own_200() throws Exception {
		User user = newUser();
		Long a = shelfItemOf(user, "정제수", "레티놀");
		Product product = fixtures.product();
		IngredientList rep = fixtures.list(product, 1, "정제수", "나이아신아마이드");

		mockMvc.perform(get("/api/compare").param("a", a.toString()).param("bProduct", product.getId().toString())
				.cookie(auth.accessTokenCookie(user)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.a.product.id").isNumber())
				.andExpect(jsonPath("$.a.listId").isNumber())
				.andExpect(jsonPath("$.b.product.id").value(product.getId()))
				.andExpect(jsonPath("$.b.listId").value(rep.getId()))
				.andExpect(jsonPath("$.common[0]").value("정제수"))
				.andExpect(jsonPath("$.onlyA[0]").value("레티놀"))
				.andExpect(jsonPath("$.onlyB[0]").value("나이아신아마이드"))
				.andExpect(jsonPath("$.groupsA[0]").value("RETINOID"))
				.andExpect(jsonPath("$.groupsB[0]").value("NIACINAMIDE"));

		mockMvc.perform(get("/api/compare").param("a", a.toString()).cookie(auth.accessTokenCookie(user)))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
	}
}
