package com.mycosmetic.client.mfds;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.concurrent.TimeUnit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import okhttp3.HttpUrl;
import okhttp3.mockwebserver.MockResponse;
import okhttp3.mockwebserver.MockWebServer;
import okhttp3.mockwebserver.RecordedRequest;

/**
 * 식약처 화장품 원료성분정보 API client.
 *
 * <p><b>주의: {@code mfds/page-1.json}은 실제 응답이 아니다.</b> 서비스 키가 아직 없어서 공공데이터포털
 * "식품의약품안전처_화장품 원료성분정보"(CsmtcsIngdCpntInfoService01) 문서 형식을 보고 만든 가정이다.
 * 가정한 것: {@code header.resultCode}("00"이 정상), {@code body.totalCount}, {@code body.items} 배열
 * (또는 {@code items.item}), 항목 필드 {@code INGR_KOR_NAME}(표준명), {@code INGR_ENG_NAME}(영문명),
 * {@code CAS_NO}, {@code INGR_SYNONYM}(이명, ", " 구분). 첫 실제 호출(사용자 승인 필요) 뒤 이 파일을 실제 응답으로 바꾸고
 * 필드 이름과 이명 구분자를 다시 확인한다(설계 9장 열린 질문).
 */
class MfdsIngredientHttpClientTest {

	private static final String KEY = "test-key+/=";

	MockWebServer server;

	@BeforeEach
	void setUp() throws IOException {
		server = new MockWebServer();
		server.start();
	}

	@AfterEach
	void tearDown() throws IOException {
		server.shutdown();
	}

	@Test
	@DisplayName("샘플 응답을 MfdsIngredientRow 목록과 totalCount로 읽는다")
	void readsSamplePage() throws Exception {
		server.enqueue(json(fixture("mfds/page-1.json")));

		MfdsPage page = client(KEY).fetchPage(1, 100);

		assertThat(page.totalCount()).isEqualTo(21345);
		assertThat(page.rows()).hasSize(5);
		MfdsIngredientRow water = page.rows().get(0);
		assertThat(water.koreanName()).isEqualTo("정제수");
		assertThat(water.englishName()).isEqualTo("Water");
		assertThat(water.casNo()).isEqualTo("7732-18-5");
		assertThat(page.rows().get(3).synonyms()).isEmpty();

		RecordedRequest request = server.takeRequest(1, TimeUnit.SECONDS);
		HttpUrl url = request.getRequestUrl();
		assertThat(url.encodedPath()).isEqualTo("/1471000/CsmtcsIngdCpntInfoService01/getCsmtcsIngdCpntInfoService01");
		assertThat(url.queryParameter("serviceKey")).isEqualTo(KEY);
		assertThat(url.queryParameter("pageNo")).isEqualTo("1");
		assertThat(url.queryParameter("numOfRows")).isEqualTo("100");
		assertThat(url.queryParameter("type")).isEqualTo("json");
	}

	@Test
	@DisplayName("이명 문자열을 구분자로 나눠 synonyms로")
	void splitsSynonyms() throws Exception {
		server.enqueue(json(fixture("mfds/page-1.json")));

		MfdsPage page = client(KEY).fetchPage(1, 100);

		// "1,2-"의 쉼표는 이름의 일부라서 나누지 않는다
		MfdsIngredientRow hexanediol = page.rows().get(2);
		assertThat(hexanediol.koreanName()).isEqualTo("1,2-헥산다이올");
		assertThat(hexanediol.synonyms()).containsExactly("1,2-헥산디올", "1,2-Dihydroxyhexane");
		assertThat(page.rows().get(1).synonyms()).containsExactly("니코틴아마이드", "니코틴산아미드", "Nicotinamide");
	}

	@Test
	@DisplayName("items.item 형태의 응답도 읽는다")
	void readsNestedItems() {
		server.enqueue(json("""
				{"header":{"resultCode":"00","resultMsg":"NORMAL SERVICE."},
				 "body":{"pageNo":2,"totalCount":"3","numOfRows":1,
				  "items":{"item":{"INGR_KOR_NAME":"글리세린","INGR_ENG_NAME":"Glycerin","CAS_NO":"56-81-5","INGR_SYNONYM":""}}}}
				"""));

		MfdsPage page = client(KEY).fetchPage(2, 1);

		assertThat(page.totalCount()).isEqualTo(3);
		assertThat(page.rows()).extracting(MfdsIngredientRow::koreanName).containsExactly("글리세린");
	}

	@Test
	@DisplayName("resultCode가 00이 아니면 예외이고 메시지에 서비스 키가 없다")
	void errorResultCode() {
		server.enqueue(json("""
				{"header":{"resultCode":"30","resultMsg":"SERVICE_KEY_IS_NOT_REGISTERED_ERROR"}}
				"""));

		assertThatThrownBy(() -> client(KEY).fetchPage(1, 100))
				.isInstanceOf(MfdsClientException.class)
				.hasMessageContaining("30")
				.hasMessageNotContaining(KEY);
	}

	@Test
	@DisplayName("JSON이 아닌 응답(XML 오류)과 5xx는 예외이고 메시지에 서비스 키가 없다")
	void nonJsonAndServerError() {
		server.enqueue(new MockResponse().setHeader("Content-Type", "text/xml")
				.setBody("<OpenAPI_ServiceResponse><cmmMsgHeader><returnReasonCode>30</returnReasonCode>"
						+ "</cmmMsgHeader></OpenAPI_ServiceResponse>"));
		server.enqueue(new MockResponse().setResponseCode(503));

		MfdsIngredientHttpClient client = client(KEY);
		assertThatThrownBy(() -> client.fetchPage(1, 100))
				.isInstanceOf(MfdsClientException.class).hasMessageNotContaining(KEY);
		assertThatThrownBy(() -> client.fetchPage(1, 100))
				.isInstanceOf(MfdsClientException.class).hasMessageNotContaining(KEY);
	}

	@Test
	@DisplayName("서비스 키가 비어 있으면 호출하지 않고 예외")
	void blankKeyDoesNotCall() {
		assertThatThrownBy(() -> client("").fetchPage(1, 100)).isInstanceOf(MfdsClientException.class);
		assertThat(server.getRequestCount()).isZero();
	}

	private MfdsIngredientHttpClient client(String key) {
		String baseUrl = server.url("/1471000/CsmtcsIngdCpntInfoService01").toString();
		return new MfdsIngredientHttpClient(baseUrl, key, Duration.ofSeconds(5));
	}

	private static MockResponse json(String body) {
		return new MockResponse().setHeader("Content-Type", "application/json;charset=UTF-8").setBody(body);
	}

	private static String fixture(String path) throws IOException {
		try (InputStream in = MfdsIngredientHttpClientTest.class.getClassLoader().getResourceAsStream(path)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}
}
