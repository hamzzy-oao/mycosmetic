package com.mycosmetic.service.shade;

import java.util.Locale;
import java.util.regex.Pattern;

import com.mycosmetic.dto.shelf.ShadeSeason;
import com.mycosmetic.dto.shelf.ShadeTone;
import com.mycosmetic.entity.PersonalColor;

/**
 * 색조 hex를 웜/쿨·계절로 나누는 단순 근사(no-ai-features 설계 E). 화면에는 "참고용"으로만 보여준다. DB·스프링 의존 없음.
 * <ul>
 * <li>hex → 색상각(0~360°), HSL 밝기(0~1), 채도 대신 크로마(최댓값-최솟값, 0~1). HSL 채도는 연한 색에서 1에 가까워져
 * "선명함"을 재기 어려워 크로마를 쓴다(연분홍 #FFB6C1은 HSL 채도 1.0, 크로마 0.29)</li>
 * <li>크로마가 {@value #GREY_CHROMA}보다 작으면(회색·검정·흰색에 가까움) NEUTRAL, 계절 없음</li>
 * <li>색상각 15° 이상 60° 이하(노랑 쪽)면 WARM, 300° 이상이거나 15° 미만(핑크·보라·푸른 빨강 쪽)이면 COOL, 그 외(초록·파랑 등) NEUTRAL</li>
 * <li>WARM: 밝기+크로마가 1 이상이면 SPRING(밝고 선명), 아니면 AUTUMN(어둡고 부드러움)</li>
 * <li>COOL: 밝기가 크로마 이상이면 SUMMER(밝고 부드러움), 아니면 WINTER(어둡고 선명)</li>
 * <li>내 퍼스널컬러와 맞음 = 웜/쿨이 같다. 계절은 맞음 판정에 쓰지 않는다. NEUTRAL은 어느 쪽과도 맞지 않는다</li>
 * </ul>
 */
public final class ShadeClassifier {

	/** 요청 DTO와 같은 형식(#RRGGBB) */
	public static final String HEX_REGEX = "^#[0-9A-Fa-f]{6}$";

	static final double GREY_CHROMA = 0.08;
	static final double WARM_FROM = 15;
	static final double WARM_TO = 60;
	static final double COOL_FROM = 300;

	private static final Pattern HEX = Pattern.compile(HEX_REGEX);

	/** @param season NEUTRAL이면 null */
	public record Shade(ShadeTone tone, ShadeSeason season) {
	}

	private ShadeClassifier() {
	}

	/** #RRGGBB가 아니면 IllegalArgumentException */
	public static String normalize(String hex) {
		if (hex == null || !HEX.matcher(hex).matches()) {
			throw new IllegalArgumentException("shade hex must be #RRGGBB");
		}
		return hex.toUpperCase(Locale.ROOT);
	}

	/** #RRGGBB가 아니면 IllegalArgumentException */
	public static Shade classify(String hex) {
		String h = normalize(hex);
		// 0~255 정수로 색상각을 계산해 경계(15°, 60°, 300°)에서 부동소수 오차가 생기지 않게 한다
		int r = Integer.parseInt(h.substring(1, 3), 16);
		int g = Integer.parseInt(h.substring(3, 5), 16);
		int b = Integer.parseInt(h.substring(5, 7), 16);
		int max = Math.max(r, Math.max(g, b));
		int min = Math.min(r, Math.min(g, b));
		if ((max - min) / 255.0 < GREY_CHROMA) {
			return new Shade(ShadeTone.NEUTRAL, null);
		}
		ShadeTone tone = toneOf(hue(r, g, b, max, max - min));
		return switch (tone) {
		// 밝기 (max+min)/510, 크로마 (max-min)/255. 비교식을 정수로 풀어 쓴다
		// 밝기+크로마 >= 1 ⇔ 3·max - min >= 510, 밝기 >= 크로마 ⇔ 3·min >= max
		case WARM -> new Shade(tone, 3 * max - min >= 510 ? ShadeSeason.SPRING : ShadeSeason.AUTUMN);
		case COOL -> new Shade(tone, 3 * min >= max ? ShadeSeason.SUMMER : ShadeSeason.WINTER);
		case NEUTRAL -> new Shade(tone, null);
		};
	}

	/** 색조나 퍼스널컬러가 없으면 null(판정 안 함) */
	public static Boolean matches(String hex, PersonalColor personalColor) {
		if (hex == null || personalColor == null) {
			return null;
		}
		ShadeTone tone = classify(hex).tone();
		return tone == (personalColor.isWarm() ? ShadeTone.WARM : ShadeTone.COOL);
	}

	/** 크로마(최댓값−최솟값, 0~1). 색조 조합의 "선명함"에 쓴다(퍼스널 탭 설계 3절 B). #RRGGBB가 아니면 IllegalArgumentException */
	public static double chromaOf(String hex) {
		String h = normalize(hex);
		int r = Integer.parseInt(h.substring(1, 3), 16);
		int g = Integer.parseInt(h.substring(3, 5), 16);
		int b = Integer.parseInt(h.substring(5, 7), 16);
		return (Math.max(r, Math.max(g, b)) - Math.min(r, Math.min(g, b))) / 255.0;
	}

	/** 색상각(0 이상 360 미만). 무채색(크로마 0)이면 0. #RRGGBB가 아니면 IllegalArgumentException */
	public static double hueOf(String hex) {
		String h = normalize(hex);
		int r = Integer.parseInt(h.substring(1, 3), 16);
		int g = Integer.parseInt(h.substring(3, 5), 16);
		int b = Integer.parseInt(h.substring(5, 7), 16);
		int max = Math.max(r, Math.max(g, b));
		int chroma = max - Math.min(r, Math.min(g, b));
		return chroma == 0 ? 0 : hue(r, g, b, max, chroma);
	}

	static ShadeTone toneOf(double hue) {
		if (hue >= WARM_FROM && hue <= WARM_TO) {
			return ShadeTone.WARM;
		}
		if (hue >= COOL_FROM || hue < WARM_FROM) {
			return ShadeTone.COOL;
		}
		return ShadeTone.NEUTRAL;
	}

	/** 0 이상 360 미만. chroma(0~255)가 0보다 클 때만 부른다 */
	private static double hue(int r, int g, int b, int max, int chroma) {
		double sector;
		if (max == r) {
			sector = (double) (g - b) / chroma;
			if (sector < 0) {
				sector += 6;
			}
		} else if (max == g) {
			sector = 2 + (double) (b - r) / chroma;
		} else {
			sector = 4 + (double) (r - g) / chroma;
		}
		return sector * 60;
	}
}
