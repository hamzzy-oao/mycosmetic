package com.mycosmetic.service.color;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;

import com.mycosmetic.entity.PersonalColor;

/**
 * 퍼스널컬러 판정, 임시 기준 v1(퍼스널 탭 설계 2절). 순수 함수라 DB 없이 단위 테스트한다.
 * <ul>
 * <li>대표 Lab = 이마 0.5 + 왼볼 0.25 + 오른볼 0.25(영역마다 프레임 평균)</li>
 * <li>색상각 h ≥ 55° → 웜, 미만 → 쿨. ITA° ≥ 41 → 밝음, 미만 → 깊음</li>
 * <li>웜+밝음 봄, 웜+깊음 가을, 쿨+밝음 여름, 쿨+깊음 겨울</li>
 * <li>두 번째 후보: 경계에 더 가까운 축을 뒤집은 계절. 축 거리는 h 10°, ITA 15에서 1.0으로 자른다</li>
 * <li>신뢰도 = min(품질 점수, 두 축 거리 중 작은 것). 0.4 미만이면 후보 순서를 정하지 않는다(ordered=false)</li>
 * </ul>
 * 피부색 측정값이 어울리는 색을 보장하지 않으므로 화면은 이 결과로 드레이핑 순서만 정하고, 최종 선택은 사용자가 한다.
 */
public final class PersonalColorRules {

	public static final String RULE_VERSION = "pc-v1";
	static final double WARM_HUE = 55;
	static final double LIGHT_ITA = 41;
	static final double HUE_SPAN = 10;
	static final double ITA_SPAN = 15;
	static final double MIN_CONFIDENCE = 0.4;
	static final Map<Region, Double> WEIGHTS = Map.of(Region.FOREHEAD, 0.5, Region.LEFT_CHEEK, 0.25,
			Region.RIGHT_CHEEK, 0.25);

	public record Judgment(List<PersonalColor> candidates, double confidence, boolean ordered) {

		public Judgment {
			candidates = List.copyOf(candidates);
		}
	}

	public record Estimate(Lab lab, double ita, double hue, double chroma, Judgment judgment,
			ColorQualityRules.Quality quality) {
	}

	private PersonalColorRules() {
	}

	/** @param whiteRef 흰 종이 기준색(없으면 null) */
	public static Estimate estimate(List<ColorSample> samples, Rgb whiteRef) {
		Map<Region, List<Lab>> labs = new EnumMap<>(Region.class);
		for (ColorSample sample : samples) {
			labs.put(sample.region(), sample.frames().stream()
					.map(rgb -> ColorMath.toLab(whiteRef == null ? rgb : ColorMath.whiteBalance(rgb, whiteRef)))
					.toList());
		}
		ColorQualityRules.Quality quality = ColorQualityRules.check(labs);
		Lab lab = representative(labs);
		double ita = ColorMath.ita(lab);
		double hue = ColorMath.hue(lab);
		return new Estimate(lab, ita, hue, ColorMath.chroma(lab), judge(hue, ita, quality.score()), quality);
	}

	public static Judgment judge(double hue, double ita, double quality) {
		boolean warm = hue >= WARM_HUE;
		boolean light = ita >= LIGHT_ITA;
		double hueDistance = Math.min(1, Math.abs(hue - WARM_HUE) / HUE_SPAN);
		double itaDistance = Math.min(1, Math.abs(ita - LIGHT_ITA) / ITA_SPAN);
		PersonalColor first = season(warm, light);
		PersonalColor second = hueDistance <= itaDistance ? season(!warm, light) : season(warm, !light);
		double confidence = Math.min(quality, Math.min(hueDistance, itaDistance));
		return new Judgment(List.of(first, second), confidence, confidence >= MIN_CONFIDENCE);
	}

	static Lab representative(Map<Region, List<Lab>> labs) {
		double l = 0;
		double a = 0;
		double b = 0;
		for (Map.Entry<Region, List<Lab>> entry : labs.entrySet()) {
			Lab mean = ColorMath.mean(entry.getValue());
			double w = WEIGHTS.get(entry.getKey());
			l += mean.l() * w;
			a += mean.a() * w;
			b += mean.b() * w;
		}
		return new Lab(l, a, b);
	}

	private static PersonalColor season(boolean warm, boolean light) {
		if (warm) {
			return light ? PersonalColor.SPRING_WARM : PersonalColor.AUTUMN_WARM;
		}
		return light ? PersonalColor.SUMMER_COOL : PersonalColor.WINTER_COOL;
	}
}
