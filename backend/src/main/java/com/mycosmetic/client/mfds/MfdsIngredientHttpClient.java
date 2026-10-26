package com.mycosmetic.client.mfds;

import java.net.http.HttpClient;
import java.time.Duration;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.regex.Pattern;

import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestClientResponseException;

import tools.jackson.core.JacksonException;
import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 공공데이터포털 "식품의약품안전처_화장품 원료성분정보" (CsmtcsIngdCpntInfoService01).
 *
 * <p>응답 필드는 실제 응답을 보기 전의 가정이다(테스트 fixture {@code mfds/page-1.json} 참고).
 * 첫 실제 호출 뒤 필드 이름과 이명 구분자를 확인한다(설계 9장).
 *
 * <p>서비스 키는 포털의 "Decoding" 키를 넣는다. 쿼리 값으로 인코딩해서 보낸다.
 */
public class MfdsIngredientHttpClient implements MfdsIngredientClient {

	static final String OPERATION = "/getCsmtcsIngdCpntInfoService01";

	/** 이명 구분자(가정): 세미콜론, 줄바꿈, 또는 뒤에 공백이 오는 쉼표. "1,2-헥산다이올"의 쉼표는 나누지 않는다 */
	private static final Pattern SYNONYM_SEPARATOR = Pattern.compile("[;\\r\\n]|,(?=\\s)");

	private final RestClient restClient;
	private final String serviceKey;
	private final JsonMapper mapper = JsonMapper.builder().build();

	public MfdsIngredientHttpClient(String baseUrl, String serviceKey, Duration timeout) {
		HttpClient httpClient = HttpClient.newBuilder()
				.version(HttpClient.Version.HTTP_1_1)
				.connectTimeout(timeout)
				.build();
		JdkClientHttpRequestFactory requestFactory = new JdkClientHttpRequestFactory(httpClient);
		requestFactory.setReadTimeout(timeout);
		this.restClient = RestClient.builder()
				.baseUrl(baseUrl)
				.requestFactory(requestFactory)
				.build();
		this.serviceKey = serviceKey;
	}

	@Override
	public MfdsPage fetchPage(int pageNo, int numOfRows) {
		if (serviceKey == null || serviceKey.isBlank()) {
			throw new MfdsClientException("식약처 서비스 키(MFDS_SERVICE_KEY)가 비어 있다");
		}
		return parse(call(pageNo, numOfRows), pageNo);
	}

	private String call(int pageNo, int numOfRows) {
		try {
			return restClient.get()
					.uri(builder -> builder.path(OPERATION)
							.queryParam("serviceKey", "{serviceKey}")
							.queryParam("pageNo", pageNo)
							.queryParam("numOfRows", numOfRows)
							.queryParam("type", "json")
							.build(serviceKey))
					.retrieve()
					.body(String.class);
		} catch (RestClientResponseException e) {
			throw new MfdsClientException("식약처 API 오류 응답. page=" + pageNo + ", status=" + e.getStatusCode().value());
		} catch (RestClientException e) {
			// 원인 메시지에 요청 주소(서비스 키 포함)가 들어 있어서 예외 종류만 남긴다
			throw new MfdsClientException("식약처 API 호출 실패. page=" + pageNo + ", error=" + e.getClass().getSimpleName());
		}
	}

	private MfdsPage parse(String body, int pageNo) {
		JsonNode root;
		try {
			root = mapper.readTree(body == null ? "" : body);
		} catch (JacksonException e) {
			throw new MfdsClientException("식약처 API 응답이 JSON이 아니다. page=" + pageNo);
		}
		if (root == null || !root.isObject()) {
			throw new MfdsClientException("식약처 API 응답이 비어 있다. page=" + pageNo);
		}
		JsonNode header = root.path("header");
		String resultCode = text(header, "resultCode");
		if (resultCode != null && !"00".equals(resultCode)) {
			throw new MfdsClientException("식약처 API 결과 코드 " + resultCode + " (" + text(header, "resultMsg")
					+ "). page=" + pageNo);
		}
		JsonNode bodyNode = root.path("body");
		int totalCount = bodyNode.path("totalCount").asInt(0);

		JsonNode items = bodyNode.path("items");
		if (items.isObject() && items.has("item")) {
			items = items.get("item");
		}
		List<MfdsIngredientRow> rows = new ArrayList<>();
		if (items.isArray()) {
			for (JsonNode item : items) {
				rows.add(row(item));
			}
		} else if (items.isObject()) {
			rows.add(row(items));
		}
		return new MfdsPage(totalCount, rows);
	}

	private static MfdsIngredientRow row(JsonNode item) {
		return new MfdsIngredientRow(
				text(item, "INGR_KOR_NAME"),
				text(item, "INGR_ENG_NAME"),
				text(item, "CAS_NO"),
				synonyms(text(item, "INGR_SYNONYM")));
	}

	static List<String> synonyms(String raw) {
		if (raw == null) {
			return List.of();
		}
		return Arrays.stream(SYNONYM_SEPARATOR.split(raw))
				.map(String::strip)
				.filter(s -> !s.isEmpty())
				.toList();
	}

	/** 값이 없거나 null이거나 공백뿐이면 null */
	private static String text(JsonNode node, String field) {
		JsonNode value = node.path(field);
		if (value.isMissingNode() || value.isNull()) {
			return null;
		}
		String s = value.asString().strip();
		return s.isEmpty() ? null : s;
	}
}
