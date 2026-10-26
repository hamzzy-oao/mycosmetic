package com.mycosmetic.service.color;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * 서버에서 다시 보는 촬영 품질(퍼스널 탭 설계 2절). 색값만으로 확인할 수 있는 것만 본다. 얼굴 수·정면 여부는 브라우저가 본다.
 * 기준(임시, 상수): 영역마다 프레임 5개 이상, 영역 안 L* 표준편차 4 이하, 좌우 볼 L* 차이 6 이하, 대표 L* 25~90
 */
public final class ColorQualityRules {

	static final int MIN_FRAMES = 5;
	static final double MAX_FRAME_STD = 4;
	static final double MAX_CHEEK_DIFF = 6;
	static final double MIN_L = 25;
	static final double MAX_L = 90;

	/**
	 * @param issues 사유 코드(TOO_FEW_FRAMES, UNSTABLE, UNEVEN_LIGHT, TOO_DARK, TOO_BRIGHT)
	 * @param score  0~1. 편차·좌우 차이가 작을수록 1에 가깝다
	 */
	public record Quality(boolean passed, List<String> issues, double score) {

		public Quality {
			issues = List.copyOf(issues);
		}
	}

	private ColorQualityRules() {
	}

	/** @param labs 영역별 프레임 Lab. 세 영역이 모두 있어야 한다 */
	public static Quality check(Map<Region, List<Lab>> labs) {
		List<String> issues = new ArrayList<>();
		if (labs.values().stream().anyMatch(frames -> frames.size() < MIN_FRAMES)) {
			issues.add("TOO_FEW_FRAMES");
		}
		double maxStd = labs.values().stream().mapToDouble(ColorQualityRules::lightnessStd).max().orElse(0);
		if (maxStd > MAX_FRAME_STD) {
			issues.add("UNSTABLE");
		}
		double left = ColorMath.mean(labs.get(Region.LEFT_CHEEK)).l();
		double right = ColorMath.mean(labs.get(Region.RIGHT_CHEEK)).l();
		double cheekDiff = Math.abs(left - right);
		if (cheekDiff > MAX_CHEEK_DIFF) {
			issues.add("UNEVEN_LIGHT");
		}
		double l = PersonalColorRules.representative(labs).l();
		if (l < MIN_L) {
			issues.add("TOO_DARK");
		} else if (l > MAX_L) {
			issues.add("TOO_BRIGHT");
		}
		double score = Math.max(0, Math.min(1, 1 - maxStd / (2 * MAX_FRAME_STD) - cheekDiff / (2 * MAX_CHEEK_DIFF)));
		return new Quality(issues.isEmpty(), issues, score);
	}

	private static double lightnessStd(List<Lab> frames) {
		double mean = frames.stream().mapToDouble(Lab::l).average().orElse(0);
		double variance = frames.stream().mapToDouble(f -> (f.l() - mean) * (f.l() - mean)).average().orElse(0);
		return Math.sqrt(variance);
	}
}
