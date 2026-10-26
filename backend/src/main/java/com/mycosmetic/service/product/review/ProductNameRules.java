package com.mycosmetic.service.product.review;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 올리브영식 제품명 표기 규칙(점검함 설계, 2026-10-24 사용자 결정: 올리브영 데이터는 가져오지 않고 표기 규칙만 따른다).
 * 제품명에는 브랜드·용량·판매 문구(기획, 증정, [단독] 등)를 넣지 않는다. 용량은 용량 칸에 따로 적는다.
 * 리필은 다른 상품이라 이름에 남기고, 중복 의심을 찾을 때만 뺀다.
 */
public final class ProductNameRules {

	/** [단독], 【기획】 같은 대괄호 표시 */
	private static final Pattern BRACKET_TAG = Pattern.compile("\\[[^\\]]*\\]|【[^】]*】");
	/** (+증정), (기획세트) 같은 괄호 속 판매 문구 */
	private static final Pattern PROMO_PAREN = Pattern
			.compile("\\([^)]*(?:증정|기획|구성|세트|단독|한정|\\+)[^)]*\\)");
	/** "1개 + 리필 2개 구성"처럼 구성품을 늘어놓은 꼬리 */
	private static final Pattern COMPOSITION_TAIL = Pattern.compile("\\s*\\d+\\s*(?:개|ea|EA|입)\\s*\\+.*$");
	private static final Pattern PROMO_WORD = Pattern.compile(
			"(?<![가-힣A-Za-z])(?:단독\\s*기획|더블\\s*기획|기획\\s*세트|기획|증정|단독|한정판|한정|1\\+1|2\\+1|세트|구성)(?![가-힣A-Za-z])");
	private static final Pattern VOLUME = Pattern.compile(
			"(?<![0-9A-Za-z.])\\d+(?:\\.\\d+)?\\s*(?:ml|mL|ML|g|G|kg|KG|l|L|매|ea|EA|개입)(?:\\s*[xX×*]\\s*\\d+)?(?![A-Za-z가-힣])");
	private static final Pattern EDGE_PUNCT = Pattern.compile("^[\\s\\-/,·+]+|[\\s\\-/,·+]+$");
	private static final Pattern SPACES = Pattern.compile("\\s+");

	public enum Reason {
		BRAND_IN_NAME("제품명 앞에 브랜드가 또 있어요"),
		VOLUME_IN_NAME("제품명에 용량이 들어 있어요"),
		PROMO_TEXT("제품명에 기획·증정 같은 판매 문구가 있어요");

		private final String message;

		Reason(String message) {
			this.message = message;
		}

		public String message() {
			return message;
		}
	}

	/**
	 * @param name    고친 제품명. 고칠 게 없으면 원래 이름
	 * @param volume  이름에서 찾은 용량. 없으면 null
	 * @param reasons 고친 이유. 비었으면 규칙에 맞는 이름
	 */
	public record Cleaned(String name, String volume, List<Reason> reasons) {
	}

	private ProductNameRules() {
	}

	public static Cleaned clean(String brand, String name) {
		String n = name == null ? "" : name;
		List<Reason> reasons = new ArrayList<>();

		String promoRemoved = PROMO_WORD.matcher(COMPOSITION_TAIL.matcher(
				PROMO_PAREN.matcher(BRACKET_TAG.matcher(n).replaceAll(" ")).replaceAll(" ")).replaceAll(""))
				.replaceAll(" ");
		if (!squash(promoRemoved).equals(squash(n))) {
			reasons.add(Reason.PROMO_TEXT);
		}
		n = promoRemoved;

		String volume = null;
		Matcher vm = VOLUME.matcher(n);
		if (vm.find()) {
			volume = SPACES.matcher(vm.group()).replaceAll("");
			n = VOLUME.matcher(n).replaceAll(" ");
			reasons.add(Reason.VOLUME_IN_NAME);
		}

		String withoutBrand = removeBrandPrefix(brand, tidy(n));
		if (!withoutBrand.equals(tidy(n))) {
			reasons.add(Reason.BRAND_IN_NAME);
		}
		n = tidy(withoutBrand);

		if (n.isEmpty()) {
			// 다 빼면 남는 게 없으면 고치자고 하지 않는다
			return new Cleaned(name, null, List.of());
		}
		reasons.sort(null);
		return new Cleaned(reasons.isEmpty() ? name : n, volume, List.copyOf(reasons));
	}

	/** 중복 의심 비교용 이름: 표기 규칙을 적용하고 리필·공백·대소문자를 뺀다 */
	public static String duplicateKey(String brand, String name) {
		return squash(clean(brand, name).name()).replace("리필", "");
	}

	/** 브랜드 비교용: 공백·대소문자를 뺀다 */
	public static String squash(String s) {
		return s == null ? "" : SPACES.matcher(s).replaceAll("").toLowerCase(Locale.ROOT);
	}

	/** 이름이 브랜드로 시작하면(공백·대소문자 무시) 그 부분을 뺀다 */
	private static String removeBrandPrefix(String brand, String name) {
		String b = squash(brand);
		if (b.isEmpty() || !squash(name).startsWith(b)) {
			return name;
		}
		int matched = 0;
		int i = 0;
		while (i < name.length() && matched < b.length()) {
			if (!Character.isWhitespace(name.charAt(i))) {
				matched++;
			}
			i++;
		}
		return name.substring(i);
	}

	private static String tidy(String s) {
		return EDGE_PUNCT.matcher(SPACES.matcher(s).replaceAll(" ").strip()).replaceAll("");
	}
}
