package com.mycosmetic.service.color;

import java.util.List;

/** sRGB(D65) → XYZ → CIELAB 변환과 각도 계산. 순수 함수 */
public final class ColorMath {

	private ColorMath() {
	}

	public static Lab toLab(Rgb rgb) {
		double r = linear(rgb.r());
		double g = linear(rgb.g());
		double b = linear(rgb.b());
		double x = (0.4124564 * r + 0.3575761 * g + 0.1804375 * b) / 0.95047;
		double y = 0.2126729 * r + 0.7151522 * g + 0.0721750 * b;
		double z = (0.0193339 * r + 0.1191920 * g + 0.9503041 * b) / 1.08883;
		double fx = f(x);
		double fy = f(y);
		double fz = f(z);
		return new Lab(116 * fy - 16, 500 * (fx - fy), 200 * (fy - fz));
	}

	/** 밝기 지표 ITA°(Individual Typology Angle) = atan2(L* − 50, b*) */
	public static double ita(Lab lab) {
		return Math.toDegrees(Math.atan2(lab.l() - 50, lab.b()));
	}

	/** 색상각 h(0 이상 360 미만). 피부는 b*가 클수록(노란 쪽) 커진다 */
	public static double hue(Lab lab) {
		double h = Math.toDegrees(Math.atan2(lab.b(), lab.a()));
		return h < 0 ? h + 360 : h;
	}

	public static double chroma(Lab lab) {
		return Math.hypot(lab.a(), lab.b());
	}

	/** 흰 종이 보정: 채널마다 255/기준값을 곱하고 0~255로 자른다. 기준값이 0인 채널은 그대로 둔다 */
	public static Rgb whiteBalance(Rgb color, Rgb white) {
		return new Rgb(scale(color.r(), white.r()), scale(color.g(), white.g()), scale(color.b(), white.b()));
	}

	public static Lab mean(List<Lab> labs) {
		double l = 0;
		double a = 0;
		double b = 0;
		for (Lab lab : labs) {
			l += lab.l();
			a += lab.a();
			b += lab.b();
		}
		return new Lab(l / labs.size(), a / labs.size(), b / labs.size());
	}

	private static int scale(int value, int reference) {
		if (reference <= 0) {
			return value;
		}
		return (int) Math.min(255, Math.round(value * 255.0 / reference));
	}

	private static double linear(int c) {
		double v = c / 255.0;
		return v <= 0.04045 ? v / 12.92 : Math.pow((v + 0.055) / 1.055, 2.4);
	}

	private static double f(double t) {
		return t > 216.0 / 24389 ? Math.cbrt(t) : (24389.0 / 27 * t + 16) / 116;
	}
}
