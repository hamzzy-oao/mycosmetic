package com.mycosmetic.controller;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.matchesPattern;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.system.CapturedOutput;
import org.springframework.boot.test.system.OutputCaptureExtension;
import org.springframework.test.web.servlet.MockMvc;

import com.mycosmetic.entity.User;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.security.JwtService;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.TestAuthHelper;

import jakarta.servlet.http.Cookie;

/** GET /api/users/me — 보안 테스트 4종 중 GET이라 1·4번(경로에 ID가 없어 3번은 해당 없음) */
@ExtendWith(OutputCaptureExtension.class)
class UserControllerSecurityTest extends IntegrationTestSupport {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TestAuthHelper auth;

	@Value("${app.jwt.secret}")
	String jwtSecret;

	@Test
	@DisplayName("로그인하지 않으면 401과 공통 오류 형식")
	void anonymous_returns401() throws Exception {
		mockMvc.perform(get("/api/users/me"))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").value(ErrorCode.UNAUTHORIZED.message()))
				.andExpect(jsonPath("$.traceId").value(matchesPattern("[0-9a-f]{8}")));
	}

	@Test
	@DisplayName("내 정보를 돌려준다")
	void me_returnsOwnInfo() throws Exception {
		User user = auth.createUser(TestAuthHelper.uniqueEmail());

		mockMvc.perform(get("/api/users/me").cookie(auth.accessTokenCookie(user)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.id").value(user.getId()))
				.andExpect(jsonPath("$.email").value(user.getEmail()))
				.andExpect(jsonPath("$.nickname").value("테스터"))
				.andExpect(jsonPath("$.hasPassword").value(true))
				.andExpect(jsonPath("$.kakaoLinked").value(false));
	}

	@Test
	@DisplayName("요청 로그에 인증된 사용자 ID가 남는다")
	void requestLog_containsUserId(CapturedOutput output) throws Exception {
		User user = auth.createUser(TestAuthHelper.uniqueEmail());

		mockMvc.perform(get("/api/users/me").cookie(auth.accessTokenCookie(user)))
				.andExpect(status().isOk());

		assertThat(output.getOut()).contains("\"userId\":\"" + user.getId() + "\"");
	}

	@Test
	@DisplayName("없는 사용자의 토큰은 401")
	void tokenOfUnknownUser_returns401() throws Exception {
		mockMvc.perform(get("/api/users/me").cookie(auth.accessTokenCookie(Long.MAX_VALUE)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	@DisplayName("만료된 access_token은 401")
	void expiredToken_returns401() throws Exception {
		User user = auth.createUser(TestAuthHelper.uniqueEmail());
		// 17분 전에 발급한 토큰(15분 만료 + 기본 허용 오차 60초를 넘김)
		Clock past = Clock.fixed(Instant.now().minus(Duration.ofMinutes(17)), ZoneOffset.UTC);
		String expired = new JwtService(jwtSecret, past).issueAccessToken(user.getId());

		mockMvc.perform(get("/api/users/me").cookie(new Cookie("access_token", expired)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}

	@Test
	@DisplayName("위조된 토큰은 401")
	void forgedToken_returns401() throws Exception {
		mockMvc.perform(get("/api/users/me").cookie(new Cookie("access_token", "a.b.c")))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"));
	}
}
