package com.mycosmetic.security.oauth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.startsWith;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.forwardedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.redirectedUrl;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpHeaders;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.mock.web.MockHttpSession;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.endpoint.OAuth2AccessTokenResponse;
import org.springframework.security.oauth2.core.user.DefaultOAuth2User;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;
import org.springframework.web.util.UriComponentsBuilder;

import com.mycosmetic.entity.Provider;
import com.mycosmetic.entity.SocialAccount;
import com.mycosmetic.entity.User;
import com.mycosmetic.repository.SocialAccountRepository;
import com.mycosmetic.repository.UserRepository;
import com.mycosmetic.service.auth.SocialAccountService;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.TestAuthHelper;

import jakarta.servlet.http.Cookie;

/**
 * 카카오 로그인·연결 전체 흐름(인가 요청 → 콜백 → 성공 처리기).
 * 카카오의 토큰 교환과 사용자 정보 조회만 가짜로 바꾼다(실제 카카오 호출 금지).
 */
class KakaoLoginFlowTest extends IntegrationTestSupport {

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TestAuthHelper auth;

	@Autowired
	UserRepository userRepository;

	@Autowired
	SocialAccountRepository socialAccountRepository;

	@Autowired
	SocialAccountService socialAccountService;

	@MockitoBean
	OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> tokenClient;

	@MockitoBean
	OAuth2UserService<OAuth2UserRequest, OAuth2User> kakaoUserService;

	/** 카카오가 이 사용자로 인증했다고 가정한다 */
	private void kakaoReturns(String kakaoId) {
		when(tokenClient.getTokenResponse(any())).thenReturn(OAuth2AccessTokenResponse.withToken("kakao-token")
				.tokenType(OAuth2AccessToken.TokenType.BEARER).expiresIn(3600).build());
		Map<String, Object> attributes = Map.of("id", kakaoId,
				"kakao_account", Map.of("email", "kakao@kakao.com", "is_email_verified", true,
						"profile", Map.of("nickname", "카카오닉")));
		when(kakaoUserService.loadUser(any())).thenReturn(
				new DefaultOAuth2User(List.of(new SimpleGrantedAuthority("OAUTH2_USER")), attributes, "id"));
	}

	/** 인가 요청을 보내고 카카오로 가는 주소의 state와 세션을 돌려준다 */
	private record Started(String state, MockHttpSession session) {
	}

	private Started start(String query, Cookie... cookies) throws Exception {
		var request = get("/oauth2/authorization/kakao" + query);
		if (cookies.length > 0) {
			request.cookie(cookies);
		}
		MvcResult result = mockMvc.perform(request)
				.andExpect(status().is3xxRedirection())
				.andExpect(header().string(HttpHeaders.LOCATION, startsWith("https://kauth.kakao.com/oauth/authorize?")))
				.andReturn();
		String location = result.getResponse().getHeader(HttpHeaders.LOCATION);
		assertThat(location).doesNotContain("link");
		String state = UriComponentsBuilder.fromUriString(location).build().getQueryParams().getFirst("state");
		return new Started(URLDecoder.decode(state, StandardCharsets.UTF_8),
				(MockHttpSession) result.getRequest().getSession(false));
	}

	/** 카카오가 돌려보낸 콜백. 브라우저는 access_token 쿠키(SameSite=Lax, Path=/)를 함께 보낸다 */
	private MockHttpServletResponse callback(Started started, String state, Cookie... cookies) throws Exception {
		var request = get("/login/oauth2/code/kakao")
				.param("code", "auth-code")
				.param("state", state)
				.session(started.session());
		if (cookies.length > 0) {
			request.cookie(cookies);
		}
		return mockMvc.perform(request)
				.andExpect(status().is3xxRedirection())
				.andReturn().getResponse();
	}

	private SocialAccount account(String kakaoId) {
		return socialAccountRepository.findByProviderAndProviderUserId(Provider.KAKAO, kakaoId).orElseThrow();
	}

	@Test
	@DisplayName("처음 카카오로 로그인하면 새 사용자로 가입하고 토큰 쿠키 2개를 받아 /shelf로 이동한다")
	void firstLogin_registersAndIssuesCookies() throws Exception {
		String kakaoId = "k-" + UUID.randomUUID();
		kakaoReturns(kakaoId);
		Started started = start("");

		MockHttpServletResponse response = callback(started, started.state());

		assertThat(response.getRedirectedUrl()).isEqualTo("/shelf");
		assertThat(response.getHeaders(HttpHeaders.SET_COOKIE))
				.anySatisfy(c -> assertThat(c).startsWith("access_token=").contains("HttpOnly", "SameSite=Lax"))
				.anySatisfy(c -> assertThat(c).startsWith("refresh_token=").contains("Path=/api/auth;",
						"SameSite=Strict"));
		User user = userRepository.findById(account(kakaoId).getUser().getId()).orElseThrow();
		assertThat(user.getNickname()).isEqualTo("카카오닉");
		assertThat(account(kakaoId).getEmail()).isEqualTo("kakao@kakao.com");
	}

	@Test
	@DisplayName("로그인한 사용자가 연결을 시작하면 콜백에서 바로 그 사용자에 연결하고 /me?linked=1로 이동한다")
	void loggedInLink_linksToSameUser() throws Exception {
		User user = auth.createUser(TestAuthHelper.uniqueEmail());
		String kakaoId = "k-" + UUID.randomUUID();
		kakaoReturns(kakaoId);
		long users = userRepository.count();
		Started started = start("?link=true", auth.accessTokenCookie(user));

		MockHttpServletResponse response = callback(started, started.state(), auth.accessTokenCookie(user));

		assertThat(response.getRedirectedUrl()).isEqualTo("/me?linked=1");
		assertThat(account(kakaoId).getUser().getId()).isEqualTo(user.getId());
		assertThat(userRepository.count()).isEqualTo(users);
		// 연결 의도는 한 번 쓰고 지운다
		assertThat(started.session().getAttribute("kakao-link:" + started.state())).isNull();
	}

	@Test
	@DisplayName("로그인하지 않고 link=true로 오면 누구에게도 연결하지 않고 일반 로그인(새 사용자)으로 처리한다")
	void anonymousLink_doesNotLinkToAnyone() throws Exception {
		User someone = auth.createUser(TestAuthHelper.uniqueEmail());
		String kakaoId = "k-" + UUID.randomUUID();
		kakaoReturns(kakaoId);
		Started started = start("?link=true");

		MockHttpServletResponse response = callback(started, started.state());

		assertThat(response.getRedirectedUrl()).isEqualTo("/shelf");
		assertThat(account(kakaoId).getUser().getId()).isNotEqualTo(someone.getId());
		assertThat(socialAccountRepository.findByUserIdAndProvider(someone.getId(), Provider.KAKAO)).isEmpty();
	}

	@Test
	@DisplayName("다른 회원에 연결된 카카오 계정으로 연결하면 연결하지 않고 /me?error=kakao로 이동한다")
	void linkKakaoOfOtherUser_redirectsWithError() throws Exception {
		String kakaoId = "k-" + UUID.randomUUID();
		Long owner = socialAccountService.loginOrRegister(kakaoId, "닉", null, null);
		User user = auth.createUser(TestAuthHelper.uniqueEmail());
		kakaoReturns(kakaoId);
		Started started = start("?link=true", auth.accessTokenCookie(user));

		MockHttpServletResponse response = callback(started, started.state(), auth.accessTokenCookie(user));

		assertThat(response.getRedirectedUrl()).isEqualTo("/me?error=kakao");
		assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).noneMatch(c -> c.startsWith("access_token="));
		assertThat(account(kakaoId).getUser().getId()).isEqualTo(owner);
	}

	@Test
	@DisplayName("연결 시작 뒤 콜백 때 다른 사용자로 로그인돼 있으면 누구에게도 연결하지 않고 /me?error=kakao")
	void linkCallbackWithOtherUsersCookie_doesNotLink() throws Exception {
		User starter = auth.createUser(TestAuthHelper.uniqueEmail());
		User other = auth.createUser(TestAuthHelper.uniqueEmail());
		String kakaoId = "k-" + UUID.randomUUID();
		kakaoReturns(kakaoId);
		long users = userRepository.count();
		Started started = start("?link=true", auth.accessTokenCookie(starter));

		MockHttpServletResponse response = callback(started, started.state(), auth.accessTokenCookie(other));

		assertThat(response.getRedirectedUrl()).isEqualTo("/me?error=kakao");
		assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).noneMatch(c -> c.startsWith("access_token="));
		assertThat(socialAccountRepository.findByProviderAndProviderUserId(Provider.KAKAO, kakaoId)).isEmpty();
		assertThat(socialAccountRepository.findByUserIdAndProvider(starter.getId(), Provider.KAKAO)).isEmpty();
		assertThat(socialAccountRepository.findByUserIdAndProvider(other.getId(), Provider.KAKAO)).isEmpty();
		assertThat(userRepository.count()).isEqualTo(users);
		assertThat(started.session().getAttribute("kakao-link:" + started.state())).isNull();
	}

	@Test
	@DisplayName("연결 시작 뒤 콜백 때 로그인 쿠키가 없으면 연결하지 않고 /me?error=kakao")
	void linkCallbackWithoutCookie_doesNotLink() throws Exception {
		User starter = auth.createUser(TestAuthHelper.uniqueEmail());
		String kakaoId = "k-" + UUID.randomUUID();
		kakaoReturns(kakaoId);
		Started started = start("?link=true", auth.accessTokenCookie(starter));

		MockHttpServletResponse response = callback(started, started.state());

		assertThat(response.getRedirectedUrl()).isEqualTo("/me?error=kakao");
		assertThat(socialAccountRepository.findByProviderAndProviderUserId(Provider.KAKAO, kakaoId)).isEmpty();
	}

	@Test
	@DisplayName("state가 맞지 않으면 로그인하지 않고 /login?error=kakao로 이동한다")
	void wrongState_redirectsToLoginError() throws Exception {
		kakaoReturns("k-" + UUID.randomUUID());
		Started started = start("");

		MockHttpServletResponse response = callback(started, "forged-state");

		assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=kakao");
		assertThat(response.getHeaders(HttpHeaders.SET_COOKIE)).noneMatch(c -> c.startsWith("access_token="));
	}

	@Test
	@DisplayName("사용자가 카카오 동의를 취소하면 /login?error=kakao로 이동한다")
	void userCancelled_redirectsToLoginError() throws Exception {
		Started started = start("");

		mockMvc.perform(get("/login/oauth2/code/kakao")
				.param("error", "access_denied")
				.param("state", started.state())
				.session(started.session()))
				.andExpect(redirectedUrl("/login?error=kakao"));
	}

	@Test
	@DisplayName("/login은 Spring 기본 로그인 페이지가 아니라 화면(index.html)으로 간다")
	void loginPath_isSpaScreen() throws Exception {
		mockMvc.perform(get("/login"))
				.andExpect(status().isOk())
				.andExpect(forwardedUrl("/index.html"));
	}

	@Test
	@DisplayName("API는 로그인하지 않으면 카카오로 보내지 않고 401 JSON")
	void api_stillReturns401() throws Exception {
		mockMvc.perform(get("/api/users/me").header(HttpHeaders.ACCEPT, "text/html"))
				.andExpect(status().isUnauthorized())
				.andExpect(header().string(HttpHeaders.CONTENT_TYPE, containsString("application/json")));
	}

	@Test
	@DisplayName("카카오 로그인 뒤에도 인증을 세션에 저장하지 않는다(세션 쿠키로는 API를 못 쓴다)")
	void sessionAloneIsNotAuthenticated() throws Exception {
		kakaoReturns("k-" + UUID.randomUUID());
		Started started = start("");
		callback(started, started.state());

		mockMvc.perform(get("/api/users/me").session(started.session()))
				.andExpect(status().isUnauthorized());
	}
}
