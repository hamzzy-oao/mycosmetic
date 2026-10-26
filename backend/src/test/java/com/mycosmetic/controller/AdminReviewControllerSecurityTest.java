package com.mycosmetic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.mycosmetic.entity.Product;
import com.mycosmetic.entity.User;
import com.mycosmetic.entity.UserRole;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.repository.ReviewDismissalRepository;
import com.mycosmetic.repository.UserRepository;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.ProductFixtures;
import com.mycosmetic.support.TestAuthHelper;

/** 관리자 점검함 API — 401, CSRF 403, 관리자가 아니면 403(기록 안 바뀜), 관리자 정상 */
class AdminReviewControllerSecurityTest extends IntegrationTestSupport {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TestAuthHelper auth;

	@Autowired
	UserRepository userRepository;

	@Autowired
	ProductFixtures fixtures;

	@Autowired
	ReviewDismissalRepository dismissalRepository;

	private User admin() {
		User user = auth.createUser(TestAuthHelper.uniqueEmail());
		ReflectionTestUtils.setField(user, "role", UserRole.ADMIN);
		return userRepository.save(user);
	}

	private static String dismissBody(Product p) {
		return "{\"productId\": " + p.getId() + ", \"type\": \"NAME_FORMAT\", \"signature\": \"x\"}";
	}

	private static void expect401(ResultActions result) throws Exception {
		result.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").value(ErrorCode.UNAUTHORIZED.message()))
				.andExpect(jsonPath("$.traceId").value(matchesPattern("[0-9a-f]{8}")));
	}

	@Test
	@DisplayName("로그인하지 않으면 401")
	void anonymous_401() throws Exception {
		Product p = fixtures.product();
		expect401(mockMvc.perform(get("/api/admin/review")));
		expect401(mockMvc.perform(post("/api/admin/review/dismissals").with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(dismissBody(p))));
		expect401(mockMvc.perform(post("/api/admin/review/fix-spacing").with(csrf())));
	}

	@Test
	@DisplayName("CSRF 토큰 없이 보낸 POST는 403이고 저장하지 않는다")
	void withoutCsrf_403() throws Exception {
		User admin = admin();
		Product p = fixtures.product();

		mockMvc.perform(post("/api/admin/review/dismissals").cookie(auth.accessTokenCookie(admin))
				.contentType(MediaType.APPLICATION_JSON).content(dismissBody(p)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CSRF_INVALID"));
		mockMvc.perform(post("/api/admin/review/fix-spacing").cookie(auth.accessTokenCookie(admin)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CSRF_INVALID"));
		assertThat(dismissalRepository.findByProductIdAndIssueType(p.getId(), "NAME_FORMAT")).isEmpty();
	}

	@Test
	@DisplayName("관리자가 아니면 403 FORBIDDEN이고 기록은 그대로")
	void ordinaryUser_403() throws Exception {
		User user = auth.createUser(TestAuthHelper.uniqueEmail());
		Product p = fixtures.product();

		mockMvc.perform(get("/api/admin/review").cookie(auth.accessTokenCookie(user)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("FORBIDDEN"));
		mockMvc.perform(post("/api/admin/review/dismissals").with(csrf()).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content(dismissBody(p)))
				.andExpect(status().isForbidden());
		mockMvc.perform(post("/api/admin/review/fix-spacing").with(csrf()).cookie(auth.accessTokenCookie(user)))
				.andExpect(status().isForbidden());
		assertThat(dismissalRepository.findByProductIdAndIssueType(p.getId(), "NAME_FORMAT")).isEmpty();
	}

	@Test
	@DisplayName("관리자: 점검 목록 200, 괜찮음 204(저장됨), 띄어쓰기 고치기 200, 모르는 종류는 400")
	void admin_ok() throws Exception {
		User admin = admin();
		String brand = "보안점검" + ProductFixtures.unique();
		Product p = fixtures.product(ProductFixtures.newBarcode(), brand, brand + " 크림");

		mockMvc.perform(get("/api/admin/review").cookie(auth.accessTokenCookie(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.checkedProducts").isNumber())
				.andExpect(jsonPath("$.counts.NAME_FORMAT").isNumber())
				.andExpect(jsonPath("$.issues[?(@.productId == " + p.getId() + ")].suggestion.name").value("크림"));

		mockMvc.perform(post("/api/admin/review/dismissals").with(csrf()).cookie(auth.accessTokenCookie(admin))
				.contentType(MediaType.APPLICATION_JSON).content(dismissBody(p)))
				.andExpect(status().isNoContent());
		assertThat(dismissalRepository.findByProductIdAndIssueType(p.getId(), "NAME_FORMAT")).isPresent();

		mockMvc.perform(post("/api/admin/review/fix-spacing").with(csrf()).cookie(auth.accessTokenCookie(admin)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.lists").isNumber());

		mockMvc.perform(post("/api/admin/review/dismissals").with(csrf()).cookie(auth.accessTokenCookie(admin))
				.contentType(MediaType.APPLICATION_JSON)
				.content("{\"productId\": " + p.getId() + ", \"type\": \"WHAT\", \"signature\": \"x\"}"))
				.andExpect(status().isBadRequest());
	}
}
