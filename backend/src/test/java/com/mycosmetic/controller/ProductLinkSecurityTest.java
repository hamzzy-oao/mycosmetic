package com.mycosmetic.controller;

import static org.hamcrest.Matchers.matchesPattern;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.csrf;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.mycosmetic.client.productpage.ProductPageClient;
import com.mycosmetic.entity.User;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.support.IntegrationTestSupport;
import com.mycosmetic.support.TestAuthHelper;

/**
 * POST /api/product-links/preview 🔒 — 401, CSRF 403, 올리브영이 아닌 주소 거절(외부 요청 없음), 정상.
 * 남의 리소스가 없는 API라 "남의 데이터" 대신 SSRF 방지를 확인한다. 실제 올리브영은 부르지 않는다.
 */
class ProductLinkSecurityTest extends IntegrationTestSupport {

	static final String LINK = "https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do?goodsNo=A000000123456";
	static final String HTML = "<meta property=\"og:title\" content=\"수분 크림 | 올리브영\">";

	@Autowired
	MockMvc mockMvc;

	@Autowired
	TestAuthHelper auth;

	@MockitoBean
	ProductPageClient productPageClient;

	private static String body(String url) {
		return "{\"url\":\"" + url + "\"}";
	}

	@Test
	@DisplayName("로그인하지 않으면 401이고 외부 요청을 하지 않는다")
	void anonymous_401() throws Exception {
		mockMvc.perform(post("/api/product-links/preview").with(csrf())
				.contentType(MediaType.APPLICATION_JSON).content(body(LINK)))
				.andExpect(status().isUnauthorized())
				.andExpect(jsonPath("$.code").value("UNAUTHORIZED"))
				.andExpect(jsonPath("$.message").value(ErrorCode.UNAUTHORIZED.message()))
				.andExpect(jsonPath("$.traceId").value(matchesPattern("[0-9a-f]{8}")));
		verify(productPageClient, never()).fetchOliveYoungDetail(anyString());
	}

	@Test
	@DisplayName("CSRF 토큰이 없으면 403이고 외부 요청을 하지 않는다")
	void withoutCsrf_403() throws Exception {
		User user = auth.createUser(TestAuthHelper.uniqueEmail());

		mockMvc.perform(post("/api/product-links/preview").cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content(body(LINK)))
				.andExpect(status().isForbidden())
				.andExpect(jsonPath("$.code").value("CSRF_INVALID"));
		verify(productPageClient, never()).fetchOliveYoungDetail(anyString());
	}

	@Test
	@DisplayName("올리브영이 아닌 주소(내부 주소 포함)는 400이고 외부 요청을 하지 않는다")
	void foreignUrl_400() throws Exception {
		User user = auth.createUser(TestAuthHelper.uniqueEmail());

		mockMvc.perform(post("/api/product-links/preview").with(csrf()).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON)
				.content(body("http://169.254.169.254/latest/meta-data?goodsNo=A000000123456")))
				.andExpect(status().isBadRequest())
				.andExpect(jsonPath("$.code").value("PRODUCT_LINK_UNSUPPORTED"));
		verify(productPageClient, never()).fetchOliveYoungDetail(anyString());
	}

	@Test
	@DisplayName("본인 요청은 200과 제품명")
	void own_200() throws Exception {
		User user = auth.createUser(TestAuthHelper.uniqueEmail());
		when(productPageClient.fetchOliveYoungDetail("A000000123456")).thenReturn(HTML);

		mockMvc.perform(post("/api/product-links/preview").with(csrf()).cookie(auth.accessTokenCookie(user))
				.contentType(MediaType.APPLICATION_JSON).content(body(LINK)))
				.andExpect(status().isOk())
				.andExpect(jsonPath("$.name").value("수분 크림"))
				.andExpect(jsonPath("$.imageUrl").doesNotExist())
				.andExpect(jsonPath("$.sourceUrl").value(LINK));
	}
}
