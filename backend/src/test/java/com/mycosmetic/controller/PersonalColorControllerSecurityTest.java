package com.mycosmetic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.ResultActions;

import com.mycosmetic.entity.PersonalColor;
import com.mycosmetic.entity.SkinType;
import com.mycosmetic.entity.User;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.repository.PersonalColorAnalysisRepository;
import com.mycosmetic.repository.SkinProfileRepository;
import com.mycosmetic.service.skin.SkinProfileService;
import com.mycosmetic.service.user.UserService;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.TestAuthHelper;

/** 퍼스널컬러 API — estimate·저장 보안 4종, latest 1·3·4번, palettes 공개(퍼스널 탭 설계 2절) */
class PersonalColorControllerSecurityTest extends IntegrationTestSupport {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TestAuthHelper auth;

	@Autowired
	PersonalColorAnalysisRepository analysisRepository;

	@Autowired
	SkinProfileRepository skinProfileRepository;

	@Autowired
	SkinProfileService skinProfileService;

	@Autowired
	UserService userService;

	/** 영역마다 같은 색 n프레임(피부색 224,172,150 → 여름 쿨 쪽) */
	static String samples(int frames, int leftR, int rightR) {
		return """
				{"samples": [
				  {"region": "FOREHEAD", "frames": %s},
				  {"region": "LEFT_CHEEK", "frames": %s},
				  {"region": "RIGHT_CHEEK", "frames": %s}
				], "client": {"faceCount": 1, "frontal": true}}""".formatted(frames(frames, 224), frames(frames, leftR),
				frames(frames, rightR));
	}

	static String frames(int n, int r) {
		StringBuilder sb = new StringBuilder("[");
		for (int i = 0; i < n; i++) {
			sb.append(i == 0 ? "" : ",").append("{\"r\":%d,\"g\":172,\"b\":150}".formatted(r));
		}
		return sb.append("]").toString();
	}

	static String saveBody(String samples, String chosen) {
		return "{\"method\": \"CAMERA\", \"chosen\": \"%s\", \"samples\": %s}".formatted(chosen, samples);
	}

	private User newUser() {
		return auth.createUser(TestAuthHelper.uniqueEmail());
	}

	private static void expect401(ResultActions result) throws Exception {
		result.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").value(ErrorCode.UNAUTHORIZED.message()))
				.andExpect(jsonPath("$.traceId").value(matchesPattern("[0-9a-f]{8}")));
	}

	private ResultActions save(User user, String body) throws Exception {
		return mockMvc.perform(post("/api/personal-color").with(csrf()).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content(body));
	}

	@Test
	@DisplayName("estimate·저장·latest: 로그인하지 않으면 401")
	void anonymous_401() throws Exception {
		expect401(mockMvc.perform(post("/api/personal-color/estimate").with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(samples(6, 224, 224))));
		expect401(mockMvc.perform(post("/api/personal-color").with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(saveBody(samples(6, 224, 224), "SUMMER_COOL"))));
		expect401(mockMvc.perform(get("/api/personal-color/latest")));
	}

	@Test
	@DisplayName("저장: CSRF 토큰이 없으면 403이고 저장하지 않는다")
	void save_noCsrf_403() throws Exception {
		User user = newUser();

		mockMvc.perform(post("/api/personal-color").cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content(saveBody(samples(6, 224, 224), "SUMMER_COOL")))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CSRF_INVALID"));
		mockMvc.perform(post("/api/personal-color/estimate").cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content(samples(6, 224, 224)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CSRF_INVALID"));
		assertThat(analysisRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(user.getId())).isEmpty();
		assertThat(skinProfileRepository.findById(user.getId())).isEmpty();
	}

	@Test
	@DisplayName("B가 저장해도 A의 결과·피부 정보는 그대로이고, latest는 자기 것만")
	void otherUser_doesNotTouchA() throws Exception {
		User a = newUser();
		User b = newUser();
		save(a, "{\"method\": \"MANUAL\", \"chosen\": \"AUTUMN_WARM\"}").andExpect(status().isOk());

		save(b, saveBody(samples(6, 224, 224), "SUMMER_COOL")).andExpect(status().isOk());

		assertThat(skinProfileRepository.findById(a.getId()).orElseThrow().getPersonalColor())
				.isEqualTo(PersonalColor.AUTUMN_WARM);
		mockMvc.perform(get("/api/personal-color/latest").cookie(auth.accessTokenCookie(a)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.chosen").value("AUTUMN_WARM"))
				.andExpect(jsonPath("$.method").value("MANUAL"));
		mockMvc.perform(get("/api/personal-color/latest").cookie(auth.accessTokenCookie(newUser())))
				.andExpect(status().isNoContent());
	}

	@Test
	@DisplayName("estimate: 200과 후보·신뢰도·품질, 저장하지 않는다")
	void estimate_own_200() throws Exception {
		User user = newUser();

		mockMvc.perform(post("/api/personal-color/estimate").with(csrf()).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content(samples(6, 224, 224)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.candidates[0]").value("SUMMER_COOL"))
				.andExpect(jsonPath("$.candidates.length()").value(2))
				.andExpect(jsonPath("$.quality.passed").value(true))
				.andExpect(jsonPath("$.lab.l").isNumber())
				.andExpect(jsonPath("$.ruleVersion").value("pc-v1"));
		assertThat(analysisRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(user.getId())).isEmpty();
	}

	@Test
	@DisplayName("estimate: 품질이 낮아도 200이고 사유를 준다(화면이 다시 찍기를 안내)")
	void estimate_lowQuality_200WithIssues() throws Exception {
		User user = newUser();

		mockMvc.perform(post("/api/personal-color/estimate").with(csrf()).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content(samples(6, 150, 250)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.quality.passed").value(false))
				.andExpect(jsonPath("$.quality.issues[0]").value("UNEVEN_LIGHT"));
	}

	@Test
	@DisplayName("저장: 본인 요청은 200, 피부 타입은 그대로 두고 퍼스널컬러만 바꾼다")
	void save_own_200_keepsSkinTypes() throws Exception {
		User user = newUser();
		skinProfileService.put(user.getId(), Set.of(SkinType.DRY), null);

		save(user, saveBody(samples(6, 224, 224), "SUMMER_COOL"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.chosen").value("SUMMER_COOL"))
				.andExpect(jsonPath("$.agreed").value(true))
				.andExpect(jsonPath("$.method").value("CAMERA"));

		var profile = skinProfileRepository.findById(user.getId()).orElseThrow();
		assertThat(profile.getPersonalColor()).isEqualTo(PersonalColor.SUMMER_COOL);
		assertThat(profile.getSkinTypes()).containsExactly(SkinType.DRY);
	}

	@Test
	@DisplayName("저장: 판정은 서버가 다시 계산한다(본문에 판정을 넣어도 무시, 고른 것이 1순위와 다르면 agreed=false)")
	void save_recomputesAgreed() throws Exception {
		User user = newUser();
		String body = "{\"method\": \"CAMERA\", \"chosen\": \"WINTER_COOL\", \"agreed\": true,"
				+ " \"candidates\": [\"WINTER_COOL\"], \"samples\": %s}".formatted(samples(6, 224, 224));

		save(user, body).andExpect(status().isOk())
				.andExpect(jsonPath("$.agreed").value(false))
				.andExpect(jsonPath("$.candidates[0]").value("SUMMER_COOL"));
	}

	@Test
	@DisplayName("저장: 프레임이 모두 같아도 좌우 볼 차이를 다시 계산해 422, 아무것도 저장하지 않는다")
	void forgedFlatFrames_stillChecked() throws Exception {
		User user = newUser();

		save(user, saveBody(samples(6, 150, 250), "SUMMER_COOL"))
				.andExpect(status().isUnprocessableContent())
				.andExpect(jsonPath("$.code").value("PERSONAL_COLOR_LOW_QUALITY"));
		save(user, saveBody(samples(3, 224, 224), "SUMMER_COOL"))
				.andExpect(status().isUnprocessableContent());
		assertThat(analysisRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(user.getId())).isEmpty();
	}

	@Test
	@DisplayName("저장: 브라우저 확인값이 얼굴 1개·정면이 아니거나 없거나 이상하면 422")
	void clientCheck_422() throws Exception {
		User user = newUser();
		String ok = samples(6, 224, 224);
		String[] bads = {
				ok.replace("\"faceCount\": 1", "\"faceCount\": 2"),
				ok.replace("\"faceCount\": 1", "\"faceCount\": -1"),
				ok.replace(", \"frontal\": true", ""),
				ok.replace(", \"client\": {\"faceCount\": 1, \"frontal\": true}", ""),
		};
		for (String bad : bads) {
			save(user, saveBody(bad, "SUMMER_COOL")).andExpect(status().isUnprocessableContent());
		}
		assertThat(analysisRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(user.getId())).isEmpty();
	}

	@Test
	@DisplayName("whiteRef가 0~255 밖이거나 프레임 원소가 null이면 400 INVALID_COLOR_SAMPLES")
	void invalidWhiteRefOrNullFrame_400() throws Exception {
		User user = newUser();
		String badWhite = samples(6, 224, 224).replace("\"client\"", "\"whiteRef\": {\"r\": 256, \"g\": 0, \"b\": 0}, \"client\"");
		String nullFrame = samples(6, 224, 224).replaceFirst("\\[\\{", "[null,{");
		for (String bad : new String[] { badWhite, nullFrame }) {
			mockMvc.perform(post("/api/personal-color/estimate").with(csrf()).cookie(auth.accessTokenCookie(user))
					.contentType(MediaType.APPLICATION_JSON).content(bad))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value("INVALID_COLOR_SAMPLES"));
		}
	}

	@Test
	@DisplayName("탈퇴하면 퍼스널컬러 분석 이력도 지워진다")
	void withdraw_deletesAnalyses() throws Exception {
		User user = newUser();
		save(user, "{\"method\": \"MANUAL\", \"chosen\": \"SPRING_WARM\"}").andExpect(status().isOk());

		userService.withdraw(user.getId());

		assertThat(analysisRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(user.getId())).isEmpty();
	}

	@Test
	@DisplayName("잘못된 색값·영역은 400 INVALID_COLOR_SAMPLES")
	void invalidSamples_400() throws Exception {
		User user = newUser();
		String[] bads = {
				samples(6, 300, 224),
				samples(6, 224, 224).replace("\"region\": \"LEFT_CHEEK\"", "\"region\": \"FOREHEAD\""),
				samples(6, 224, 224).replace("{\"region\": \"RIGHT_CHEEK\", \"frames\": " + frames(6, 224) + "}", ""),
				samples(0, 224, 224),
				samples(31, 224, 224),
				"{\"samples\": []}",
		};
		for (String bad : bads) {
			mockMvc.perform(post("/api/personal-color/estimate").with(csrf()).cookie(auth.accessTokenCookie(user))
					.contentType(MediaType.APPLICATION_JSON).content(bad))
					.andExpect(status().isBadRequest())
					.andExpect(jsonPath("$.code").value(org.hamcrest.Matchers.oneOf("INVALID_COLOR_SAMPLES", "INVALID_REQUEST")));
		}
		save(user, "{\"method\": \"CAMERA\", \"chosen\": \"SUMMER_COOL\"}")
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("INVALID_COLOR_SAMPLES"));
	}

	@Test
	@DisplayName("직접 고르기(MANUAL)는 색 없이 저장한다")
	void manual_save() throws Exception {
		User user = newUser();

		save(user, "{\"method\": \"MANUAL\", \"chosen\": \"SPRING_WARM\"}")
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.method").value("MANUAL"))
				.andExpect(jsonPath("$.agreed").doesNotExist())
				.andExpect(jsonPath("$.lab").doesNotExist());
		assertThat(analysisRepository.findFirstByUserIdOrderByCreatedAtDescIdDesc(user.getId()).orElseThrow()
				.getLabL()).isNull();
	}

	@Test
	@DisplayName("공개는 GET palettes 하나뿐: POST palettes, GET /api/personal-color는 로그인 없이 401")
	void onlyPalettesIsPublic() throws Exception {
		expect401(mockMvc.perform(post("/api/personal-color/palettes").with(csrf())));
		expect401(mockMvc.perform(get("/api/personal-color")));
	}

	@Test
	@DisplayName("palettes: 로그인 없이 200, 계절마다 8색")
	void palettes_public() throws Exception {
		mockMvc.perform(get("/api/personal-color/palettes"))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.palettes.SPRING_WARM.length()").value(8))
				.andExpect(jsonPath("$.palettes.WINTER_COOL[0]").value("#800020"));
	}
}
