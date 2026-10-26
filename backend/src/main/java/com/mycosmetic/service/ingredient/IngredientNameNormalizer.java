package com.mycosmetic.service.ingredient;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 성분 이름 정규화 (설계 4.2). 정규화는 이 클래스에서만 한다.
 * extraction-eval 스킬 {@code metrics.py}의 {@code normalize()}와 같은 순서·문자 집합을 쓴다. 한쪽을 바꾸면 다른 쪽도 바꾼다.
 *
 * <ol>
 * <li>NFC</li>
 * <li>괄호 {@code (...)}·{@code （...）} 내용을 떼어 parentheticals에 넣고 그 자리는 공백</li>
 * <li>하이픈류 → {@code -}, 가운뎃점류 → {@code ·}</li>
 * <li>연속 공백을 하나로, 앞뒤 공백 제거</li>
 * <li>소문자</li>
 * </ol>
 */
public final class IngredientNameNormalizer {

	private static final String HYPHENS = "\u2010\u2011\u2012\u2013\u2014\u2015\u2212\uFF0D\uFE63";
	private static final String MIDDLE_DOTS = "\u00B7\u2022\u2027\u30FB\uFF65\u318D";
	private static final Pattern PAREN = Pattern.compile("\\(([^)]*)\\)|\uFF08([^\uFF09]*)\uFF09");
	/** 파이썬 str의 \s(str.isspace)와 같은 범위: 유니코드 공백 + 0x1C~0x1F */
	private static final Pattern WHITESPACE = Pattern.compile("[\\s\\x1C-\\x1F]+", Pattern.UNICODE_CHARACTER_CLASS);

	private IngredientNameNormalizer() {
	}

	public static NormalizedName normalize(String raw) {
		String s = Normalizer.normalize(raw == null ? "" : raw, Normalizer.Form.NFC);

		List<String> parentheticals = new ArrayList<>();
		Matcher m = PAREN.matcher(s);
		StringBuilder sb = new StringBuilder();
		while (m.find()) {
			String inner = m.group(1) != null ? m.group(1) : m.group(2);
			String cleaned = collapse(inner);
			if (!cleaned.isEmpty()) {
				parentheticals.add(cleaned);
			}
			m.appendReplacement(sb, " ");
		}
		m.appendTail(sb);

		String value = collapse(unify(sb.toString())).toLowerCase(Locale.ROOT);
		return new NormalizedName(value, parentheticals);
	}

	private static String unify(String s) {
		StringBuilder sb = new StringBuilder(s.length());
		for (int i = 0; i < s.length(); i++) {
			char c = s.charAt(i);
			if (HYPHENS.indexOf(c) >= 0) {
				sb.append('-');
			} else if (MIDDLE_DOTS.indexOf(c) >= 0) {
				sb.append('\u00B7');
			} else {
				sb.append(c);
			}
		}
		return sb.toString();
	}

	private static String collapse(String s) {
		return WHITESPACE.matcher(s).replaceAll(" ").strip();
	}
}
