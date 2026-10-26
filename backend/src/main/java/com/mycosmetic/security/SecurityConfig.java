package com.mycosmetic.security;

import java.time.Clock;

import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.convert.converter.Converter;
import org.springframework.http.HttpMethod;
import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.config.ObjectPostProcessor;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.client.web.OAuth2AuthorizedClientRepository;
import org.springframework.security.oauth2.core.user.OAuth2User;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.server.resource.InvalidBearerTokenException;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.context.RequestAttributeSecurityContextRepository;
import org.springframework.security.web.csrf.CsrfFilter;

import com.mycosmetic.security.oauth.KakaoLinkAuthorizationRequestResolver;
import com.mycosmetic.security.oauth.OAuth2LoginFailureHandler;
import com.mycosmetic.security.oauth.OAuth2LoginSuccessHandler;
import com.mycosmetic.service.user.UserService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import tools.jackson.databind.json.JsonMapper;

/**
 * 보안 설정(설계 5.2, 5.4).
 * - 인증: access_token 쿠키의 JWT(리소스 서버). Authorization 헤더는 읽지 않는다
 * - 인증 결과는 세션이 아니라 요청 속성(RequestAttributeSecurityContextRepository)에만 둔다.
 *   RequestLoggingFilter가 이 속성에서 userId(JWT sub)를 읽는다. 세션은 OAuth2 핸드셰이크만 쓴다
 * - 카카오 로그인·연결(설계 5.3): oauth2Login. 성공하면 우리 토큰 쿠키를 주고 화면으로 이동한다.
 *   카카오 토큰은 로그인 뒤 쓰지 않으므로 어디에도 저장하지 않는다
 * - CSRF: XSRF-TOKEN 쿠키를 X-XSRF-TOKEN 헤더로 보낸다(csrf.spa()). 예외 경로 없음
 * - 401·403은 공통 오류 형식 JSON
 * 웹 서버가 없는 eval 프로필(평가 실행기)에서는 HttpSecurity가 없으므로 만들지 않는다.
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication
public class SecurityConfig {

	@Bean
	JwtDecoder jwtDecoder(JwtService jwtService) {
		return jwtService.decoder();
	}

	@Bean
	SecurityFilterChain securityFilterChain(HttpSecurity http, JwtDecoder jwtDecoder, UserService userService, Clock clock,
			JsonMapper jsonMapper, ClientRegistrationRepository clientRegistrationRepository,
			OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> kakaoAccessTokenResponseClient,
			OAuth2UserService<OAuth2UserRequest, OAuth2User> kakaoOAuth2UserService,
			OAuth2LoginSuccessHandler oauth2LoginSuccessHandler, OAuth2LoginFailureHandler oauth2LoginFailureHandler)
			throws Exception {
		JsonAuthenticationEntryPoint entryPoint = new JsonAuthenticationEntryPoint(jsonMapper);
		JsonAccessDeniedHandler accessDeniedHandler = new JsonAccessDeniedHandler(jsonMapper);
		http
				.authorizeHttpRequests(auth -> auth
						.requestMatchers(HttpMethod.GET, "/api/auth/csrf", "/api/auth/email-available",
								"/api/skin-analysis/questions", "/api/personal-color/palettes").permitAll()
						.requestMatchers(HttpMethod.POST, "/api/auth/signup", "/api/auth/login", "/api/auth/refresh",
								"/api/auth/logout").permitAll()
						.requestMatchers("/actuator/health", "/oauth2/**", "/login/oauth2/**", "/error").permitAll()
						.requestMatchers("/api/**").authenticated()
						.requestMatchers(HttpMethod.GET, "/**").permitAll()
						.anyRequest().authenticated())
				.csrf(csrf -> csrf.spa().withObjectPostProcessor(CSRF_FOR_COOKIE_TOKENS))
				.securityContext(context -> context
						.securityContextRepository(new RequestAttributeSecurityContextRepository()))
				// 401마다 세션에 요청을 저장하지 않는다
				.requestCache(cache -> cache.disable())
				// 로그아웃은 POST /api/auth/logout(AuthController)이 한다
				.logout(logout -> logout.disable())
				.oauth2ResourceServer(resource -> resource
						.bearerTokenResolver(new CookieBearerTokenResolver())
						.authenticationEntryPoint(entryPoint)
						.accessDeniedHandler(accessDeniedHandler)
						.jwt(jwt -> jwt.decoder(jwtDecoder)
								.jwtAuthenticationConverter(existingUserJwtConverter(userService))))
				.oauth2Login(oauth -> oauth
						// 화면의 /login(React)과 겹치는 기본 로그인 페이지를 만들지 않는다
						.loginPage("/login")
						.authorizationEndpoint(endpoint -> endpoint.authorizationRequestResolver(
								new KakaoLinkAuthorizationRequestResolver(clientRegistrationRepository, jwtDecoder,
										userService, clock)))
						.tokenEndpoint(endpoint -> endpoint.accessTokenResponseClient(kakaoAccessTokenResponseClient))
						.userInfoEndpoint(endpoint -> endpoint.userService(kakaoOAuth2UserService))
						.authorizedClientRepository(NO_AUTHORIZED_CLIENT_STORAGE)
						.successHandler(oauth2LoginSuccessHandler)
						.failureHandler(oauth2LoginFailureHandler))
				.exceptionHandling(exceptions -> exceptions
						.authenticationEntryPoint(entryPoint)
						.accessDeniedHandler(accessDeniedHandler));
		return http.build();
	}

	/**
	 * 리소스 서버 설정은 Bearer 토큰을 찾은 요청을 CSRF 검사에서 뺀다(Authorization 헤더는 브라우저가 자동으로 붙이지 않는다는 가정).
	 * 우리 토큰은 브라우저가 자동으로 보내는 access_token 쿠키라 그 예외가 있으면 로그인한 사용자의 모든 상태 변경 요청이
	 * CSRF 검사 없이 통과한다. 그래서 그 예외를 지우고 기본 규칙(GET·HEAD·TRACE·OPTIONS 외 모두 검사)으로 되돌린다.
	 * 주의: 이 처리기는 검사 대상 matcher를 통째로 바꾸므로 csrf.ignoringRequestMatchers(...)로 넣은 예외도 함께 덮어쓴다.
	 * CSRF 예외 경로를 추가하려면 ignoringRequestMatchers가 아니라 여기의 matcher를 바꿔야 한다
	 * (예: DEFAULT_CSRF_MATCHER와 "예외 경로가 아님"을 AndRequestMatcher로 묶기). 회귀 테스트: CsrfRegressionTest.
	 */
	private static final ObjectPostProcessor<CsrfFilter> CSRF_FOR_COOKIE_TOKENS = new ObjectPostProcessor<>() {

		@Override
		public <O extends CsrfFilter> O postProcess(O filter) {
			filter.setRequireCsrfProtectionMatcher(CsrfFilter.DEFAULT_CSRF_MATCHER);
			return filter;
		}
	};

	/**
	 * 카카오 access token(OAuth2AuthorizedClient)을 보관하지 않는다. 기본값은 메모리에 사용자마다 쌓아 두는데,
	 * 로그인 뒤 카카오 API를 부르지 않으므로 필요 없다.
	 */
	private static final OAuth2AuthorizedClientRepository NO_AUTHORIZED_CLIENT_STORAGE = new OAuth2AuthorizedClientRepository() {

		@Override
		public <T extends OAuth2AuthorizedClient> T loadAuthorizedClient(String clientRegistrationId,
				Authentication principal, HttpServletRequest request) {
			return null;
		}

		@Override
		public void saveAuthorizedClient(OAuth2AuthorizedClient authorizedClient, Authentication principal,
				HttpServletRequest request, HttpServletResponse response) {
		}

		@Override
		public void removeAuthorizedClient(String clientRegistrationId, Authentication principal,
				HttpServletRequest request, HttpServletResponse response) {
		}
	};

	/**
	 * sub가 숫자인 사용자 ID이고 그 사용자가 아직 있을 때만 인증한다(탈퇴 직후 남은 토큰 거절).
	 * 인증 이름(auth.getName())은 sub, 즉 사용자 ID다.
	 */
	private static Converter<Jwt, AbstractAuthenticationToken> existingUserJwtConverter(UserService userService) {
		JwtAuthenticationConverter delegate = new JwtAuthenticationConverter();
		return jwt -> {
			Long userId;
			try {
				userId = Long.valueOf(jwt.getSubject());
			} catch (NumberFormatException e) {
				throw new InvalidBearerTokenException("sub가 사용자 ID가 아니다");
			}
			if (!userService.exists(userId)) {
				throw new InvalidBearerTokenException("없는 사용자");
			}
			return delegate.convert(jwt);
		};
	}
}
