package com.mycosmetic.service.product;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import tools.jackson.databind.JsonNode;
import tools.jackson.databind.json.JsonMapper;

/**
 * 올리브영 상품정보 제공고시 JSON에서 전성분·용량·개봉 후 사용기간을 꺼낸다.
 * 형식: {@code data.articleInfoList[].items[] = {title, content}}. 항목 이름은 화장품법 표시 항목 그대로다.
 *
 * @param ingredientsText "화장품법에 따라 기재해야 하는 모든 성분" 원문. 없으면 빈 문자열
 * @param volume          "내용물의 용량 또는 중량". 없으면 null
 * @param paoMonths       "개봉 후 12개월"처럼 적힌 개월 수(1~60). 없으면 null
 */
record OliveYoungArticle(String ingredientsText, String volume, Integer paoMonths) {

	private static final JsonMapper MAPPER = JsonMapper.builder().build();
	/** 상세 페이지 안 옵션 목록의 바코드. HTML 속 JSON이 이스케이프돼 있을 수도, 아닐 수도 있다 */
	private static final Pattern STANDARD_CODE = Pattern.compile("\\\\?\"standardCode\\\\?\"\\s*:\\s*\\\\?\"(\\d{8,14})\\\\?\"");
	private static final Pattern PAO = Pattern.compile("개봉\\s*(?:후|이후)[^0-9]{0,15}(\\d{1,2})\\s*(?:개월|M\\b|m\\b)");
	static final int MAX_VOLUME_LENGTH = 50;

	/** 상세 페이지 HTML에서 첫 옵션의 바코드. 없으면 null */
	static String standardCode(String html) {
		Matcher m = STANDARD_CODE.matcher(html == null ? "" : html);
		return m.find() ? m.group(1) : null;
	}

	/** 형식이 다르거나 JSON이 아니면 IllegalArgumentException */
	static OliveYoungArticle parse(String json) {
		JsonNode root;
		try {
			root = MAPPER.readTree(json);
		} catch (RuntimeException e) {
			throw new IllegalArgumentException("상품정보 제공고시 JSON이 아니다", e);
		}
		JsonNode lists = root.path("data").path("articleInfoList");
		if (!lists.isArray()) {
			throw new IllegalArgumentException("articleInfoList 없음");
		}
		List<String> texts = new ArrayList<>();
		String volume = null;
		Integer pao = null;
		for (JsonNode list : lists) {
			for (JsonNode item : list.path("items")) {
				String title = item.path("title").asText("");
				String content = item.path("content").asText("").strip();
				if (content.isEmpty()) {
					continue;
				}
				if (title.contains("모든 성분")) {
					// 상품정보 묶음이 여러 개면(세트 구성품) 묶음 이름을 구역 이름으로 붙인다
					texts.add(lists.size() > 1 ? "[" + list.path("title").asText("") + "]\n" + content : content);
				} else if (volume == null && title.contains("용량")) {
					volume = content.length() <= MAX_VOLUME_LENGTH ? content : content.substring(0, MAX_VOLUME_LENGTH);
				} else if (pao == null && title.contains("사용기한")) {
					pao = paoMonths(content);
				}
			}
		}
		return new OliveYoungArticle(String.join("\n", texts), volume, pao);
	}

	static Integer paoMonths(String text) {
		Matcher m = PAO.matcher(text);
		if (!m.find()) {
			return null;
		}
		int months = Integer.parseInt(m.group(1));
		return months >= 1 && months <= 60 ? months : null;
	}
}
