package com.mycosmetic.service.ingredient;

import java.text.Normalizer;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 전성분 글(상세페이지 상품정보 제공고시, 사용자가 붙여넣은 글)을 옵션별 구역과 성분 이름으로 나눈다.
 * 쇼핑몰마다 표기가 달라 줄 단위로 본다. 실제로 본 형식:
 *
 * <ul>
 * <li>{@code [크림] 정제수, ...} 이름과 성분이 한 줄 (이름 뒤 공백이 없을 수도 있음)</li>
 * <li>{@code [에센셜 토너]} 다음 줄에 성분</li>
 * <li>{@code [01 데이지]} 아래 {@code 1. 탤크, ...}, {@code 2. ...} 팔레트 칸마다 한 줄</li>
 * <li>{@code 1. 그린마일드 업 선 플러스 50ml(본품+구성품)} 또는 {@code SS 1} 같은 쉼표 없는 이름 줄</li>
 * <li>{@code 퍼펙트트윈립#901트루피치 ㅣ 아이소도데케인, ...} 구분 기호로 이름과 성분을 나눈 줄</li>
 * </ul>
 *
 * 성분은 쉼표로 나누되 숫자 사이 쉼표({@code 1,2-헥산다이올})와 괄호 안 쉼표는 이름의 일부로 둔다.
 */
public final class IngredientTextSections {

	public static final int MAX_SECTIONS = 30;
	public static final int MAX_NAMES = 200;
	static final int MAX_NAME_LENGTH = 200;
	static final int MAX_LABEL_LENGTH = 60;

	/** 구역 하나. 구역이 하나뿐이거나 이름 줄이 없으면 label은 null */
	public record Section(String label, List<String> names) {
		public Section {
			names = List.copyOf(names);
		}
	}

	private static final Pattern BRACKET_HEAD = Pattern.compile("^\\[([^\\]]{1,60})\\]\\s*(.*)$");
	private static final Pattern NUMBER_HEAD = Pattern.compile("^(\\d{1,2})[.)]\\s*(.*)$");
	private static final Pattern PIPE_HEAD = Pattern.compile("^([^,，]{1,60}?)\\s*[ㅣ|｜]\\s*(.+)$");
	private static final Pattern TITLE_PREFIX = Pattern.compile("^(전성분|모든\\s*성분|성분)\\s*[:：]\\s*");
	private static final Pattern PLUS_MINUS = Pattern.compile("^(\\+\\s*/\\s*-|±|\\+/−)\\s*[:：]?\\s*");

	/** 나누는 중인 구역. 다음 줄이 같은 목록의 이어짐이면 names에 더한다 */
	private static final class Draft {
		final String label;
		final List<String> names = new ArrayList<>();

		Draft(String label) {
			this.label = label;
		}
	}

	private IngredientTextSections() {
	}

	public static List<Section> parse(String text) {
		String normalized = Normalizer.normalize(text == null ? "" : text, Normalizer.Form.NFC)
				.replace("\r\n", "\n").replace('\r', '\n').replace('\t', ' ');
		List<Draft> sections = new ArrayList<>();
		String top = null;
		String sub = null;
		Draft current = null;
		for (String rawLine : normalized.split("\n")) {
			String line = TITLE_PREFIX.matcher(rawLine.strip()).replaceFirst("");
			if (line.isEmpty()) {
				continue;
			}
			Matcher bracket = BRACKET_HEAD.matcher(line);
			if (bracket.matches() && !isPlusMinus(bracket.group(1))) {
				String label = bracket.group(1).strip();
				String rest = bracket.group(2).strip();
				top = label;
				sub = null;
				current = null;
				if (isList(rest)) {
					current = add(sections, label, rest);
				}
				continue;
			}
			Matcher number = NUMBER_HEAD.matcher(line);
			if (number.matches()) {
				String rest = number.group(2).strip();
				if (isList(rest)) {
					current = add(sections, join(top, sub == null ? number.group(1) + "번" : sub), rest);
					sub = null;
				} else {
					sub = shorten(rest.isEmpty() ? number.group(1) + "번" : rest);
					current = null;
				}
				continue;
			}
			Matcher pipe = PIPE_HEAD.matcher(line);
			if (pipe.matches() && isList(pipe.group(2))) {
				current = add(sections, join(top, pipe.group(1).strip()), pipe.group(2));
				sub = null;
				continue;
			}
			if (!isList(line)) {
				if (line.length() <= MAX_LABEL_LENGTH) {
					sub = line;
					current = null;
				}
				continue;
			}
			if (current != null) {
				current.names.addAll(splitNames(line)); // 한 목록이 여러 줄로 이어짐
			} else {
				current = add(sections, join(top, sub), line);
				sub = null;
			}
		}
		if (sections.isEmpty()) {
			// 쉼표가 없는 한 줄짜리(성분 하나) 글
			List<String> names = splitNames(normalized.replace('\n', ','));
			return names.isEmpty() ? List.of() : List.of(new Section(null, cap(names)));
		}
		List<Section> result = sections.stream()
				.filter(d -> !d.names.isEmpty())
				.limit(MAX_SECTIONS)
				.map(d -> new Section(d.label, cap(d.names)))
				.toList();
		return result.size() == 1 ? List.of(new Section(null, result.get(0).names())) : result;
	}

	/** 쉼표로 나눈 성분 이름. 숫자 사이 쉼표와 괄호 안 쉼표는 나누지 않는다 */
	public static List<String> splitNames(String text) {
		List<String> names = new ArrayList<>();
		StringBuilder sb = new StringBuilder();
		int depth = 0;
		for (int i = 0; i < text.length(); i++) {
			char c = text.charAt(i);
			if (c == '(' || c == '（') {
				depth++;
			} else if ((c == ')' || c == '）') && depth > 0) {
				depth--;
			}
			boolean separator = (c == ',' || c == '，' || c == '、') && depth == 0
					&& !(i > 0 && i + 1 < text.length() && Character.isDigit(text.charAt(i - 1))
							&& Character.isDigit(text.charAt(i + 1)));
			if (separator) {
				addName(names, sb.toString());
				sb.setLength(0);
			} else {
				sb.append(c);
			}
		}
		addName(names, sb.toString());
		return names;
	}

	private static void addName(List<String> names, String raw) {
		String name = raw.replace("[", " ").replace("]", " ").strip();
		name = PLUS_MINUS.matcher(name).replaceFirst("");
		name = name.replaceAll("^[-•*·]+\\s*", "").replaceAll("[.。]+$", "").strip();
		if (name.isEmpty() || name.equals("등") || name.length() > MAX_NAME_LENGTH) {
			return;
		}
		names.add(name);
	}

	private static Draft add(List<Draft> sections, String label, String text) {
		Draft draft = new Draft(label);
		draft.names.addAll(splitNames(text));
		sections.add(draft);
		return draft;
	}

	private static boolean isList(String s) {
		return s.indexOf(',') >= 0 || s.indexOf('，') >= 0 || s.indexOf('、') >= 0;
	}

	private static boolean isPlusMinus(String label) {
		return PLUS_MINUS.matcher(label.strip()).lookingAt();
	}

	private static String join(String top, String sub) {
		if (top == null) return sub;
		if (sub == null) return top;
		return top + " · " + sub;
	}

	private static String shorten(String s) {
		return s.length() <= MAX_LABEL_LENGTH ? s : s.substring(0, MAX_LABEL_LENGTH);
	}

	private static List<String> cap(List<String> names) {
		return names.size() <= MAX_NAMES ? names : names.subList(0, MAX_NAMES);
	}
}
