package com.mycosmetic.service.product;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.List;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.web.util.UriComponentsBuilder;

import com.mycosmetic.client.productpage.ProductPageClient;
import com.mycosmetic.client.productpage.ProductPageClientException;
import com.mycosmetic.dto.ingredient.IngredientSection;
import com.mycosmetic.dto.product.ProductLinkPreviewResponse;
import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.service.ingredient.IngredientTextService;

/**
 * 올리브영 상품 상세 링크에서 제품명·사진을 채우고, 설정이 켜져 있으면 상품정보 제공고시에서 전성분·용량·
 * 개봉 후 사용기간까지 채운다. 정해진 호스트로만 요청한다(SSRF 방지). 전성분을 못 가져와도 제품명·사진은 돌려주고
 * 붙여넣기로 안내한다.
 */
@Service
public class ProductLinkService {

	private static final Logger log = LoggerFactory.getLogger(ProductLinkService.class);

	private static final Set<String> OLIVE_YOUNG_HOSTS = Set.of("www.oliveyoung.co.kr", "oliveyoung.co.kr",
			"m.oliveyoung.co.kr");
	private static final Pattern GOODS_NO = Pattern.compile("[A-Z]\\d{12}");
	private static final Pattern META = Pattern.compile("<meta\\s+[^>]*>", Pattern.CASE_INSENSITIVE);
	private static final Pattern ATTR = Pattern.compile("(property|name|content)\\s*=\\s*\"([^\"]*)\"",
			Pattern.CASE_INSENSITIVE);
	private static final String TITLE_SUFFIX = " | 올리브영";
	private static final String IMAGE_PREFIX = "https://image.oliveyoung.co.kr/";
	private static final String DETAIL_URL = "https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do?goodsNo=";

	static final String NOTICE_BASIC = "제품명과 사진을 가져왔어요. 전성분은 상세페이지에서 복사해 붙여넣어 주세요. 사진은 미리보기로만 표시돼요.";
	static final String NOTICE_NO_INGREDIENTS = "제품명과 사진을 가져왔어요. 전성분은 가져오지 못했어요. 상세페이지의 전성분을 복사해 붙여넣어 주세요.";
	static final String NOTICE_INGREDIENTS = "제품명·사진·전성분을 가져왔어요. 내 제품과 같은지 확인해 주세요.";

	private final ProductPageClient client;
	private final IngredientTextService ingredientTextService;
	private final boolean ingredientsEnabled;

	@Autowired
	public ProductLinkService(ProductPageClient client, IngredientTextService ingredientTextService,
			@Value("${app.product-page.oliveyoung-ingredients:false}") boolean ingredientsEnabled) {
		this.client = client;
		this.ingredientTextService = ingredientTextService;
		this.ingredientsEnabled = ingredientsEnabled;
	}

	/** 전성분 가져오기를 끈 상태(제품명·사진만) */
	public ProductLinkService(ProductPageClient client) {
		this(client, null, false);
	}

	/**
	 * 올리브영 상품 상세 링크가 아니면 PRODUCT_LINK_UNSUPPORTED, 페이지를 못 가져오거나 제품명이 없으면 PRODUCT_LINK_FETCH_FAILED
	 */
	public ProductLinkPreviewResponse preview(String url) {
		String goodsNo = oliveYoungGoodsNo(url);
		String html;
		try {
			html = client.fetchOliveYoungDetail(goodsNo);
		} catch (ProductPageClientException e) {
			log.warn("상품 상세 링크 가져오기 실패. goodsNo={}, reason={}", goodsNo, e.getMessage());
			throw new BusinessException(ErrorCode.PRODUCT_LINK_FETCH_FAILED);
		}
		String title = meta(html, "og:title");
		if (title == null) {
			log.warn("상품 상세 페이지에 og:title이 없다. goodsNo={}", goodsNo);
			throw new BusinessException(ErrorCode.PRODUCT_LINK_FETCH_FAILED);
		}
		String name = title.endsWith(TITLE_SUFFIX) ? title.substring(0, title.length() - TITLE_SUFFIX.length()).strip()
				: title;
		String image = meta(html, "og:image");
		String imageUrl = image != null && image.startsWith(IMAGE_PREFIX) ? image : null;
		String sourceUrl = DETAIL_URL + goodsNo;
		if (!ingredientsEnabled || ingredientTextService == null) {
			return new ProductLinkPreviewResponse(name, imageUrl, sourceUrl, NOTICE_BASIC, List.of(), null, null);
		}
		OliveYoungArticle article = article(goodsNo, html);
		List<IngredientSection> sections = article == null ? List.of()
				: ingredientTextService.matchSections(article.ingredientsText());
		String notice = sections.isEmpty() ? NOTICE_NO_INGREDIENTS
				: sections.size() > 1 ? "제품명·사진·전성분을 가져왔어요. 전성분에 옵션이 " + sections.size()
						+ "개 있어요. 내 제품의 옵션을 골라 주세요."
						: NOTICE_INGREDIENTS;
		return new ProductLinkPreviewResponse(name, imageUrl, sourceUrl, notice, sections,
				article == null ? null : article.volume(), article == null ? null : article.paoMonths());
	}

	/**
	 * 링크의 쇼핑몰 표기 제품명만 가져온다(전성분은 안 가져온다). 새 제품의 이름이 링크 표기 그대로인지 확인할 때 쓴다
	 * (2026-10-26 제품명 예외처리). 지원하지 않는 링크이거나 못 가져오면 null
	 */
	public String officialName(String url) {
		try {
			String title = meta(client.fetchOliveYoungDetail(oliveYoungGoodsNo(url)), "og:title");
			if (title == null) {
				return null;
			}
			return title.endsWith(TITLE_SUFFIX) ? title.substring(0, title.length() - TITLE_SUFFIX.length()).strip()
					: title;
		} catch (BusinessException | ProductPageClientException e) {
			log.warn("링크 제품명 확인 실패. reason={}", e.getMessage());
			return null;
		}
	}

	/** 상품정보 제공고시. 바코드를 못 찾거나 호출·해석에 실패하면 null(제품명·사진은 그대로 돌려준다) */
	private OliveYoungArticle article(String goodsNo, String html) {
		String standardCode = OliveYoungArticle.standardCode(html);
		if (standardCode == null) {
			log.warn("상품 상세 페이지에서 옵션 바코드를 찾지 못했다. goodsNo={}", goodsNo);
			return null;
		}
		try {
			return OliveYoungArticle.parse(client.fetchOliveYoungArticle(goodsNo, standardCode));
		} catch (ProductPageClientException | IllegalArgumentException e) {
			log.warn("상품정보 제공고시 가져오기 실패. goodsNo={}, reason={}", goodsNo, e.getMessage());
			return null;
		}
	}

	/** 링크에서 상품 번호만 뽑는다. https/http, 올리브영 호스트, goodsNo 형식이 모두 맞아야 한다 */
	static String oliveYoungGoodsNo(String url) {
		URI uri;
		try {
			uri = new URI(url.strip());
		} catch (URISyntaxException | NullPointerException e) {
			throw new BusinessException(ErrorCode.PRODUCT_LINK_UNSUPPORTED);
		}
		String scheme = uri.getScheme();
		String host = uri.getHost();
		if (scheme == null || host == null || !(scheme.equalsIgnoreCase("https") || scheme.equalsIgnoreCase("http"))
				|| !OLIVE_YOUNG_HOSTS.contains(host.toLowerCase()) || uri.getUserInfo() != null
				|| (uri.getPort() != -1 && uri.getPort() != 443 && uri.getPort() != 80)) {
			throw new BusinessException(ErrorCode.PRODUCT_LINK_UNSUPPORTED);
		}
		String goodsNo = UriComponentsBuilder.fromUri(uri).build().getQueryParams().getFirst("goodsNo");
		if (goodsNo == null || !GOODS_NO.matcher(goodsNo).matches()) {
			throw new BusinessException(ErrorCode.PRODUCT_LINK_UNSUPPORTED);
		}
		return goodsNo;
	}

	/** {@code <meta property="key" content="...">}의 content. 없거나 비어 있으면 null */
	static String meta(String html, String key) {
		Matcher tags = META.matcher(html);
		while (tags.find()) {
			String property = null;
			String content = null;
			Matcher attrs = ATTR.matcher(tags.group());
			while (attrs.find()) {
				String attr = attrs.group(1).toLowerCase();
				if (attr.equals("content")) {
					content = attrs.group(2);
				} else {
					property = attrs.group(2);
				}
			}
			if (key.equalsIgnoreCase(property) && content != null) {
				String value = unescape(content).strip();
				return value.isEmpty() ? null : value;
			}
		}
		return null;
	}

	private static String unescape(String s) {
		return s.replace("&quot;", "\"").replace("&#39;", "'").replace("&#x27;", "'").replace("&lt;", "<")
				.replace("&gt;", ">").replace("&amp;", "&");
	}
}
