package com.mycosmetic.security.oauth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnWebApplication;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.client.endpoint.OAuth2AccessTokenResponseClient;
import org.springframework.security.oauth2.client.endpoint.OAuth2AuthorizationCodeGrantRequest;
import org.springframework.security.oauth2.client.endpoint.RestClientAuthorizationCodeTokenResponseClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.oauth2.client.registration.InMemoryClientRegistrationRepository;
import org.springframework.security.oauth2.client.userinfo.DefaultOAuth2UserService;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserRequest;
import org.springframework.security.oauth2.client.userinfo.OAuth2UserService;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.user.OAuth2User;

/**
 * 카카오 OAuth2 클라이언트 등록(설계 5.1, 5.3).
 * spring.security.oauth2.client.registration 속성 대신 코드로 등록한다. Spring Boot는 client-id가 비어 있으면
 * 시작하지 않는데, 개발 .env의 KAKAO_CLIENT_ID가 빈 값이어도 앱과 테스트가 떠야 하기 때문이다.
 * 비어 있으면 자리표시 값을 쓰고 경고를 남긴다(카카오 로그인만 실패한다).
 * 토큰 교환·사용자 정보 조회는 빈으로 두어 테스트에서 가짜로 바꿔 끼운다(실제 카카오 호출 금지).
 */
@Configuration(proxyBeanMethods = false)
@ConditionalOnWebApplication
public class KakaoOAuth2Config {

	private static final Logger log = LoggerFactory.getLogger(KakaoOAuth2Config.class);
	public static final String REGISTRATION_ID = "kakao";
	static final String CLIENT_ID_NOT_SET = "kakao-client-id-not-set";

	@Bean
	ClientRegistrationRepository clientRegistrationRepository(@Value("${app.kakao.client-id:}") String clientId,
			@Value("${app.kakao.client-secret:}") String clientSecret) {
		if (clientId == null || clientId.isBlank()) {
			log.warn("KAKAO_CLIENT_ID가 비어 있어 카카오 로그인을 쓸 수 없다");
			clientId = CLIENT_ID_NOT_SET;
		}
		return new InMemoryClientRegistrationRepository(kakao(clientId, clientSecret == null ? "" : clientSecret));
	}

	static ClientRegistration kakao(String clientId, String clientSecret) {
		return ClientRegistration.withRegistrationId(REGISTRATION_ID)
				.clientName("Kakao")
				.clientId(clientId)
				.clientSecret(clientSecret)
				.clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_POST)
				.authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE)
				.redirectUri("{baseUrl}/login/oauth2/code/{registrationId}")
				.scope("profile_nickname", "account_email")
				.authorizationUri("https://kauth.kakao.com/oauth/authorize")
				.tokenUri("https://kauth.kakao.com/oauth/token")
				.userInfoUri("https://kapi.kakao.com/v2/user/me")
				.userNameAttributeName("id")
				.build();
	}

	/** 인가 코드 → 카카오 access token 교환 */
	@Bean
	OAuth2AccessTokenResponseClient<OAuth2AuthorizationCodeGrantRequest> kakaoAccessTokenResponseClient() {
		return new RestClientAuthorizationCodeTokenResponseClient();
	}

	/** 카카오 사용자 정보(/v2/user/me) 조회 */
	@Bean
	OAuth2UserService<OAuth2UserRequest, OAuth2User> kakaoOAuth2UserService() {
		return new DefaultOAuth2UserService();
	}
}
