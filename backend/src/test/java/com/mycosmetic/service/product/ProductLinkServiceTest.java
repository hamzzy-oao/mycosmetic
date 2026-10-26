package com.mycosmetic.service.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import com.mycosmetic.client.productpage.ProductPageClient;
import com.mycosmetic.client.productpage.ProductPageClientException;
import com.mycosmetic.dto.product.ProductLinkPreviewResponse;
import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.entity.MatchType;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.service.ingredient.IngredientMatcher;
import com.mycosmetic.service.ingredient.IngredientTextService;
import com.mycosmetic.service.ingredient.MatchResult;

class ProductLinkServiceTest {

	static final String LINK = "https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do?goodsNo=A000000123456"
			+ "&dispCatNo=90000010001&trackingCd=Home_Curation1_1&t_page=%ED%99%88";

	static final String DETAIL_WITH_OPTION = "<meta property=\"og:title\" content=\"선크림 | 올리브영\">"
			+ "<script>self.__next_f.push([1,\"{\\\"options\\\":[{\\\"standardCode\\\":\\\"8809838657262\\\","
			+ "\\\"optionName\\\":\\\"50ml\\\"}]}\"])</script>";

	/** 받은 상품 번호를 기록하고 정해 둔 HTML·상품정보를 돌려준다. null이면 호출 실패 */
	static class FakeClient implements ProductPageClient {
		final List<String> calls = new ArrayList<>();
		final List<String> articleCalls = new ArrayList<>();
		final String html;
		final String article;

		FakeClient(String html) {
			this(html, null);
		}

		FakeClient(String html, String article) {
			this.html = html;
			this.article = article;
		}

		@Override
		public String fetchOliveYoungDetail(String goodsNo) {
			calls.add(goodsNo);
			if (html == null) {
				throw new ProductPageClientException("실패");
			}
			return html;
		}

		@Override
		public String fetchOliveYoungArticle(String goodsNo, String standardCode) {
			articleCalls.add(goodsNo + "/" + standardCode);
			if (article == null) {
				throw new ProductPageClientException("실패");
			}
			return article;
		}
	}

	static String fixture() throws IOException {
		return resource("/oliveyoung/goods-detail.html");
	}

	static String resource(String path) throws IOException {
		try (InputStream in = ProductLinkServiceTest.class.getResourceAsStream(path)) {
			return new String(in.readAllBytes(), StandardCharsets.UTF_8);
		}
	}

	/** 매칭은 사전 없이 모두 UNMATCHED로 둔다(구역 나누기·응답 모양만 본다) */
	static ProductLinkService withIngredients(FakeClient client) {
		IngredientMatcher matcher = mock(IngredientMatcher.class);
		when(matcher.match(anyList())).thenAnswer(inv -> {
			List<String> names = inv.getArgument(0);
			List<MatchResult> results = new ArrayList<>();
			for (int i = 0; i < names.size(); i++) {
				results.add(new MatchResult(i + 1, names.get(i), MatchType.UNMATCHED, null, null, List.of()));
			}
			return results;
		});
		return new ProductLinkService(client, new IngredientTextService(matcher), true);
	}

	@Test
	@DisplayName("전성분 켜짐: 상세 페이지의 옵션 바코드로 상품정보를 불러 전성분·용량·개봉 후 사용기간을 채운다")
	void ingredients_single() throws IOException {
		FakeClient client = new FakeClient(DETAIL_WITH_OPTION, resource("/oliveyoung/article-sunscreen-numbered-header.json"));

		ProductLinkPreviewResponse response = withIngredients(client).preview(LINK);

		assertThat(client.articleCalls).containsExactly("A000000123456/8809838657262");
		assertThat(response.name()).isEqualTo("선크림");
		assertThat(response.ingredientSections()).hasSize(1);
		assertThat(response.ingredientSections().get(0).label()).isNull();
		assertThat(response.ingredientSections().get(0).items()).extracting(MatchResult::rawText)
				.startsWith("정제수", "징크옥사이드").contains("1,2-헥산다이올");
		assertThat(response.volume()).isEqualTo("50mL+20mL(증정)");
		assertThat(response.paoMonths()).isEqualTo(12);
		assertThat(response.notice()).isEqualTo(ProductLinkService.NOTICE_INGREDIENTS);
	}

	@Test
	@DisplayName("옵션(호수)이 여러 개면 구역마다 돌려주고 옵션을 고르라고 안내한다")
	void ingredients_options() throws IOException {
		FakeClient client = new FakeClient(DETAIL_WITH_OPTION, resource("/oliveyoung/article-cushion-12-shades.json"));

		ProductLinkPreviewResponse response = withIngredients(client).preview(LINK);

		assertThat(response.ingredientSections()).hasSize(12);
		assertThat(response.ingredientSections().get(0).label()).isEqualTo("크림");
		assertThat(response.notice()).contains("옵션이 12개");
		assertThat(response.paoMonths()).isEqualTo(12);
	}

	@Test
	@DisplayName("상품정보 호출이 실패하거나 바코드가 없으면 제품명·사진만 주고 붙여넣기로 안내한다")
	void ingredients_failed() {
		FakeClient failing = new FakeClient(DETAIL_WITH_OPTION, null);
		FakeClient noCode = new FakeClient("<meta property=\"og:title\" content=\"토너\">", "{}");

		ProductLinkPreviewResponse a = withIngredients(failing).preview(LINK);
		ProductLinkPreviewResponse b = withIngredients(noCode).preview(LINK);

		assertThat(a.name()).isEqualTo("선크림");
		assertThat(a.ingredientSections()).isEmpty();
		assertThat(a.notice()).isEqualTo(ProductLinkService.NOTICE_NO_INGREDIENTS);
		assertThat(b.ingredientSections()).isEmpty();
		assertThat(noCode.articleCalls).isEmpty();
	}

	@Test
	@DisplayName("전성분 꺼짐: 상품정보를 부르지 않는다")
	void ingredients_disabled() {
		FakeClient client = new FakeClient(DETAIL_WITH_OPTION, "{}");

		ProductLinkPreviewResponse response = new ProductLinkService(client).preview(LINK);

		assertThat(client.articleCalls).isEmpty();
		assertThat(response.ingredientSections()).isEmpty();
		assertThat(response.notice()).isEqualTo(ProductLinkService.NOTICE_BASIC);
	}

	@Test
	@DisplayName("개봉 후 사용기간 글에서 개월 수만 읽는다")
	void paoMonths() {
		assertThat(OliveYoungArticle.paoMonths("제조일로부터 36개월 (개봉 후 12개월)")).isEqualTo(12);
		assertThat(OliveYoungArticle.paoMonths("개봉 후 6M")).isEqualTo(6);
		assertThat(OliveYoungArticle.paoMonths("제조일로부터 36개월")).isNull();
		assertThat(OliveYoungArticle.paoMonths("개봉 후 99개월")).isNull();
	}

	private static ErrorCode codeOf(Throwable e) {
		return ((BusinessException) e).errorCode();
	}

	@Test
	@DisplayName("상세 링크에서 상품 번호만 뽑아 요청하고, 제품명(접미사 제거)·사진·정리한 주소를 돌려준다")
	void preview_ok() throws IOException {
		FakeClient client = new FakeClient(fixture());

		ProductLinkPreviewResponse response = new ProductLinkService(client).preview(LINK);

		assertThat(client.calls).containsExactly("A000000123456");
		assertThat(response.name()).isEqualTo("[대용량] 테스트 브랜드 수분 크림 100ml & 미니");
		assertThat(response.imageUrl()).startsWith("https://image.oliveyoung.co.kr/");
		assertThat(response.sourceUrl())
				.isEqualTo("https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do?goodsNo=A000000123456");
	}

	@Test
	@DisplayName("모바일 주소도 받는다")
	void mobileLink() {
		assertThat(ProductLinkService.oliveYoungGoodsNo(
				"https://m.oliveyoung.co.kr/m/goods/getGoodsDetail.do?goodsNo=A000000123456"))
				.isEqualTo("A000000123456");
	}

	@ParameterizedTest
	@ValueSource(strings = {
			"https://evil.example.com/store/goods/getGoodsDetail.do?goodsNo=A000000123456",
			"https://www.oliveyoung.co.kr.evil.com/?goodsNo=A000000123456",
			"https://user@www.oliveyoung.co.kr/?goodsNo=A000000123456",
			"https://www.oliveyoung.co.kr:8443/?goodsNo=A000000123456",
			"file:///etc/passwd",
			"ftp://www.oliveyoung.co.kr/?goodsNo=A000000123456",
			"https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do",
			"https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do?goodsNo=../../admin",
			"http://127.0.0.1/?goodsNo=A000000123456",
			"아무 글자",
	})
	@DisplayName("올리브영 상세 링크가 아니면 요청하지 않고 PRODUCT_LINK_UNSUPPORTED")
	void unsupported(String url) {
		FakeClient client = new FakeClient("<html></html>");

		assertThatThrownBy(() -> new ProductLinkService(client).preview(url))
				.satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.PRODUCT_LINK_UNSUPPORTED));
		assertThat(client.calls).isEmpty();
	}

	@Test
	@DisplayName("페이지를 못 가져오거나 제품명이 없으면 PRODUCT_LINK_FETCH_FAILED")
	void fetchFailed() {
		assertThatThrownBy(() -> new ProductLinkService(new FakeClient(null)).preview(LINK))
				.satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.PRODUCT_LINK_FETCH_FAILED));
		assertThatThrownBy(() -> new ProductLinkService(new FakeClient("<html><head></head></html>")).preview(LINK))
				.satisfies(e -> assertThat(codeOf(e)).isEqualTo(ErrorCode.PRODUCT_LINK_FETCH_FAILED));
	}

	@Test
	@DisplayName("사진 주소가 올리브영 이미지 서버가 아니면 버린다")
	void foreignImage_dropped() {
		String html = "<meta property=\"og:title\" content=\"토너\">"
				+ "<meta property=\"og:image\" content=\"https://evil.example.com/a.jpg\">";

		ProductLinkPreviewResponse response = new ProductLinkService(new FakeClient(html)).preview(LINK);

		assertThat(response.name()).isEqualTo("토너");
		assertThat(response.imageUrl()).isNull();
	}
}
