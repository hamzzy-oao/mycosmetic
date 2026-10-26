package com.mycosmetic.client.productpage;

import java.net.http.HttpClient;
import java.time.Duration;

import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

/**
 * 올리브영 상품 상세 페이지(/store/goods/getGoodsDetail.do)와 상품정보 제공고시(/goods/api/v1/article).
 * 사용자가 링크를 넣을 때 그 상품 1건만 부른다. 상품정보 제공고시는 공개 API가 아니고 robots.txt도 허용하지 않아
 * 설정(app.product-page.oliveyoung-ingredients)으로 끌 수 있게 했다. 다른 호스트로의 리다이렉트는 따라가지 않는다.
 */
public class OliveYoungPageHttpClient implements ProductPageClient {

	static final String BASE_URL = "https://www.oliveyoung.co.kr";
	static final String DETAIL_PATH = "/store/goods/getGoodsDetail.do";
	static final String ARTICLE_PATH = "/goods/api/v1/article";
	/** 이보다 긴 응답은 앞부분만 본다. og 태그는 head에 있다 */
	static final int MAX_CHARS = 2_000_000;

	private final RestClient restClient;

	public OliveYoungPageHttpClient(Duration timeout) {
		HttpClient httpClient = HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_1_1)
				.followRedirects(HttpClient.Redirect.NEVER)
				.connectTimeout(timeout)
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(timeout);
		this.restClient = RestClient.builder()
				.baseUrl(BASE_URL)
				.requestFactory(requestFactory)
				.defaultHeader("User-Agent", "Mozilla/5.0 (compatible; MyVanity/1.0)")
				.build();
	}

	@Override
	public String fetchOliveYoungDetail(String goodsNo) {
		return fetch(BASE_URL + DETAIL_PATH + "?goodsNo=" + goodsNo);
	}

	/** goodsNo·standardCode는 호출하는 쪽이 형식을 검증한 값이라 JSON에 그대로 넣어도 된다 */
	@Override
	public String fetchOliveYoungArticle(String goodsNo, String standardCode) {
		String json = "{\"goodsNumber\":\"" + goodsNo + "\",\"liquorFlag\":false,"
				+ "\"goodsOptionInfoList\":[{\"standardCode\":\"" + standardCode + "\",\"optionName\":\"\"}]}";
		return call(() -> restClient.post()
				.uri(java.net.URI.create(BASE_URL + ARTICLE_PATH))
				.contentType(MediaType.APPLICATION_JSON)
				.accept(MediaType.APPLICATION_JSON)
				.header("Referer", BASE_URL + DETAIL_PATH + "?goodsNo=" + goodsNo)
				.body(json)
				.retrieve()
				.body(String.class));
	}

	private String fetch(String url) {
		return call(() -> restClient.get()
				.uri(java.net.URI.create(url))
				.accept(MediaType.TEXT_HTML)
				.retrieve()
				.body(String.class));
	}

	private String call(java.util.function.Supplier<String> request) {
		String body;
		try {
			body = request.get();
		} catch (RestClientResponseException e) {
			throw new ProductPageClientException("상품 페이지 오류 응답. status=" + e.getStatusCode().value());
		} catch (RestClientException e) {
			throw new ProductPageClientException("상품 페이지 호출 실패. error=" + e.getClass().getSimpleName());
		}
		if (body == null || body.isBlank()) {
			throw new ProductPageClientException("상품 페이지 응답이 비어 있다");
		}
		return body.length() > MAX_CHARS ? body.substring(0, MAX_CHARS) : body;
	}
}
