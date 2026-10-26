package com.mycosmetic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.mycosmetic.dto.diary.DiaryEntryRequest;
import com.mycosmetic.entity.DiaryMood;
import com.mycosmetic.entity.DiaryTag;
import com.mycosmetic.entity.SkinDiaryEntry;
import com.mycosmetic.entity.User;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.repository.SkinDiaryEntryRepository;
import com.mycosmetic.service.diary.SkinDiaryService;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.TestAuthHelper;

/** 피부 일기 API — PUT·DELETE 보안 테스트 4종, GET 1·4번 */
class SkinDiaryControllerSecurityTest extends IntegrationTestSupport {

	static final String BODY = "{\"mood\": \"BAD\", \"tags\": [\"DRYNESS\", \"TROUBLE\"], \"memo\": \" 턱에 하나 \"}";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TestAuthHelper auth;

	@Autowired
	SkinDiaryService diaryService;

	@Autowired
	SkinDiaryEntryRepository diaryRepository;

	private User newUser() {
		return auth.createUser(TestAuthHelper.uniqueEmail());
	}

	private static LocalDate today() {
		return LocalDate.now(ZoneId.of("Asia/Seoul"));
	}

	private static void expect401(ResultActions result) throws Exception {
		result.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").value(ErrorCode.UNAUTHORIZED.message()))
				.andExpect(jsonPath("$.traceId").value(matchesPattern("[0-9a-f]{8}")));
	}

	@Test
	@DisplayName("GET·PUT·DELETE: 로그인하지 않으면 401")
	void anonymous_401() throws Exception {
		String path = "/api/diary/" + today();
		expect401(mockMvc.perform(get("/api/diary")));
		expect401(mockMvc.perform(put(path).with(csrf()).contentType(MediaType.APPLICATION_JSON).content(BODY)));
		expect401(mockMvc.perform(delete(path).with(csrf())));
	}

	@Test
	@DisplayName("PUT·DELETE: CSRF 토큰이 없으면 403이고 바뀌지 않는다")
	void withoutCsrf_403() throws Exception {
		User user = newUser();
		LocalDate day = today().minusDays(1);
		diaryService.put(user.getId(), day, new DiaryEntryRequest(DiaryMood.GOOD, Set.of(), null));

		mockMvc.perform(put("/api/diary/" + today()).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CSRF_INVALID"));
		mockMvc.perform(delete("/api/diary/" + day).cookie(auth.accessTokenCookie(user)))
				.andExpect(status().isForbidden());

		assertThat(diaryRepository.findByUserIdAndEntryDate(user.getId(), today())).isEmpty();
		assertThat(diaryRepository.findByUserIdAndEntryDate(user.getId(), day)).isPresent();
	}

	@Test
	@DisplayName("B가 A와 같은 날짜로 PUT·DELETE해도 A의 기록은 그대로")
	void otherUser_doesNotTouchA() throws Exception {
		User a = newUser();
		User b = newUser();
		LocalDate day = today();
		diaryService.put(a.getId(), day, new DiaryEntryRequest(DiaryMood.GOOD, Set.of(DiaryTag.OILINESS), "A 메모"));

		mockMvc.perform(delete("/api/diary/" + day).with(csrf()).cookie(auth.accessTokenCookie(b)))
				.andExpect(status().isNotFound())
				.andExpect(jsonPath("$.code").value("NOT_FOUND"));
		mockMvc.perform(put("/api/diary/" + day).with(csrf()).cookie(auth.accessTokenCookie(b))
				.contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isOk());

		SkinDiaryEntry kept = diaryRepository.findByUserIdAndEntryDate(a.getId(), day).orElseThrow();
		assertThat(kept.getMood()).isEqualTo(DiaryMood.GOOD);
		assertThat(kept.getTags()).containsExactly(DiaryTag.OILINESS);
		assertThat(kept.getMemo()).isEqualTo("A 메모");
		mockMvc.perform(get("/api/diary").cookie(auth.accessTokenCookie(b)))
				.andExpect(jsonPath("$.entries.length()").value(1))
				.andExpect(jsonPath("$.entries[0].mood").value("BAD"));
	}

	@Test
	@DisplayName("본인: PUT 200 → GET에 보이고 → DELETE 204. 미래 날짜·없는 기분은 400")
	void own_flow() throws Exception {
		User user = newUser();
		LocalDate day = today();

		mockMvc.perform(put("/api/diary/" + day).with(csrf()).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.date").value(day.toString()))
				.andExpect(jsonPath("$.mood").value("BAD"))
				.andExpect(jsonPath("$.tags[0]").value("TROUBLE"))
				.andExpect(jsonPath("$.tags[1]").value("DRYNESS"))
				.andExpect(jsonPath("$.memo").value("턱에 하나"));

		mockMvc.perform(get("/api/diary").param("month", day.toString().substring(0, 7))
				.cookie(auth.accessTokenCookie(user)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.month").value(day.toString().substring(0, 7)))
				.andExpect(jsonPath("$.entries.length()").value(1))
				.andExpect(jsonPath("$.entries[0].date").value(day.toString()));

		mockMvc.perform(delete("/api/diary/" + day).with(csrf()).cookie(auth.accessTokenCookie(user)))
				.andExpect(status().isNoContent());
		assertThat(diaryRepository.findByUserIdAndEntryDate(user.getId(), day)).isEmpty();

		mockMvc.perform(put("/api/diary/" + day.plusDays(1)).with(csrf()).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content(BODY))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_REQUEST"));
		mockMvc.perform(put("/api/diary/" + day).with(csrf()).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content("{\"tags\": []}"))
				.andExpect(status().isBadRequest());
		mockMvc.perform(get("/api/diary").param("month", "2026-13").cookie(auth.accessTokenCookie(user)))
				.andExpect(status().isBadRequest());
	}
}
