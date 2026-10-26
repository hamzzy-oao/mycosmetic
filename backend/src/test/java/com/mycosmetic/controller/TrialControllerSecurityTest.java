package com.mycosmetic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.mycosmetic.dto.trial.TrialResponse;
import com.mycosmetic.entity.User;
import com.mycosmetic.entity.Verdict;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.repository.ProductTrialRepository;
import com.mycosmetic.repository.ShelfItemRepository;
import com.mycosmetic.service.shelf.ShelfService;
import com.mycosmetic.service.trial.TrialService;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.ProductFixtures;
import com.mycosmetic.support.TestAuthHelper;

/** 새 제품 테스트 API — POST·DELETE 보안 테스트 4종, GET 1·4번 */
class TrialControllerSecurityTest extends IntegrationTestSupport {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TestAuthHelper auth;

	@Autowired
	ProductFixtures fixtures;

	@Autowired
	ShelfService shelfService;

	@Autowired
	TrialService trialService;

	@Autowired
	ProductTrialRepository trialRepository;

	@Autowired
	ShelfItemRepository shelfItemRepository;

	private User newUser() {
		return auth.createUser(TestAuthHelper.uniqueEmail());
	}

	private Long shelfItem(User user) {
		return shelfService.add(user.getId(), fixtures.list(fixtures.product(), 1, "정제수").getId()).id();
	}

	private static String startBody(Long shelfItemId) {
		return "{\"shelfItemId\": " + shelfItemId + "}";
	}

	private static void expect401(ResultActions result) throws Exception {
		result.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").value(ErrorCode.UNAUTHORIZED.message()))
				.andExpect(jsonPath("$.traceId").value(matchesPattern("[0-9a-f]{8}")));
	}

	@Test
	@DisplayName("GET·POST·finish·DELETE: 로그인하지 않으면 401")
	void anonymous_401() throws Exception {
		expect401(mockMvc.perform(get("/api/trials")));
		expect401(mockMvc.perform(post("/api/trials").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content(startBody(1L))));
		expect401(mockMvc.perform(post("/api/trials/1/finish").with(csrf()).contentType(MediaType.APPLICATION_JSON)
				.content("{\"verdict\": \"GOOD\"}")));
		expect401(mockMvc.perform(delete("/api/trials/1").with(csrf())));
	}

	@Test
	@DisplayName("POST·finish·DELETE: CSRF 토큰이 없으면 403이고 바뀌지 않는다")
	void withoutCsrf_403() throws Exception {
		User user = newUser();
		Long itemId = shelfItem(user);
		Long otherItemId = shelfItem(user);
		TrialResponse trial = trialService.start(user.getId(), itemId);

		mockMvc.perform(post("/api/trials").cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content(startBody(otherItemId)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CSRF_INVALID"));
		mockMvc.perform(post("/api/trials/" + trial.id() + "/finish").cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content("{\"verdict\": \"GOOD\"}"))
				.andExpect(status().isForbidden());
		mockMvc.perform(delete("/api/trials/" + trial.id()).cookie(auth.accessTokenCookie(user)))
				.andExpect(status().isForbidden());

		assertThat(trialRepository.existsByShelfItemId(otherItemId)).isFalse();
		assertThat(trialRepository.findById(trial.id()).orElseThrow().getFinishedAt()).isNull();
		assertThat(shelfItemRepository.findById(itemId).orElseThrow().getVerdict()).isEqualTo(Verdict.UNRATED);
	}

	@Test
	@DisplayName("B가 A의 화장대 항목·테스트 ID로 요청하면 404이고 A의 데이터는 그대로")
	void otherUser_404() throws Exception {
		User a = newUser();
		User b = newUser();
		Long aItem = shelfItem(a);
		Long aOtherItem = shelfItem(a);
		TrialResponse trial = trialService.start(a.getId(), aItem);

		mockMvc.perform(post("/api/trials").with(csrf()).cookie(auth.accessTokenCookie(b))
				.contentType(MediaType.APPLICATION_JSON).content(startBody(aOtherItem)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		mockMvc.perform(post("/api/trials/" + trial.id() + "/finish").with(csrf()).cookie(auth.accessTokenCookie(b))
				.contentType(MediaType.APPLICATION_JSON).content("{\"verdict\": \"BAD\"}"))
				.andExpect(status().isNotFound());
		mockMvc.perform(delete("/api/trials/" + trial.id()).with(csrf()).cookie(auth.accessTokenCookie(b)))
				.andExpect(status().isNotFound());
		mockMvc.perform(get("/api/trials").cookie(auth.accessTokenCookie(b)))
				.andExpect(jsonPath("$.length()").value(0));

		assertThat(trialRepository.existsByShelfItemId(aOtherItem)).isFalse();
		assertThat(trialRepository.findById(trial.id()).orElseThrow().getFinishedAt()).isNull();
		assertThat(shelfItemRepository.findById(aItem).orElseThrow().getVerdict()).isEqualTo(Verdict.UNRATED);
	}

	@Test
	@DisplayName("본인: POST 201 → GET → finish 200(평가 바뀜) → 중복 409 → DELETE 204")
	void own_flow() throws Exception {
		User user = newUser();
		Long itemId = shelfItem(user);

		String created = mockMvc.perform(post("/api/trials").with(csrf()).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content(startBody(itemId)))
				.andExpect(status().isCreated())
				.andExpect(jsonPath("$.shelfItemId").value(itemId))
				.andExpect(jsonPath("$.status").value("ACTIVE"))
				.andExpect(jsonPath("$.day").value(1))
				.andExpect(jsonPath("$.totalDays").value(14))
				.andExpect(jsonPath("$.summary.recordedDays").value(0))
				.andReturn().getResponse().getContentAsString();
		Long trialId = Long.valueOf(created.replaceAll(".*?\"id\":(\\d+).*", "$1"));

		mockMvc.perform(get("/api/trials").cookie(auth.accessTokenCookie(user)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.length()").value(1))
				.andExpect(jsonPath("$[0].product.name").exists());

		mockMvc.perform(post("/api/trials/" + trialId + "/finish").with(csrf()).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content("{\"verdict\": \"GOOD\"}"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.status").value("DONE"))
				.andExpect(jsonPath("$.verdict").value("GOOD"));
		assertThat(shelfItemRepository.findById(itemId).orElseThrow().getVerdict()).isEqualTo(Verdict.GOOD);

		mockMvc.perform(post("/api/trials").with(csrf()).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content(startBody(itemId)))
				.andExpect(status().isConflict())
				.andExpect(jsonPath("$.code").value("TRIAL_DUPLICATE"));

		mockMvc.perform(delete("/api/trials/" + trialId).with(csrf()).cookie(auth.accessTokenCookie(user)))
				.andExpect(status().isNoContent());
		assertThat(trialRepository.findById(trialId)).isEmpty();
		assertThat(shelfItemRepository.findById(itemId)).isPresent();
	}
}
