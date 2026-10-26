package com.mycosmetic.service.color;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

import java.util.Collections;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import com.mycosmetic.dto.shelf.ShadeSeason;
import com.mycosmetic.entity.PersonalColor;
import com.mycosmetic.service.shade.ShadeClassifier;

/** 퍼스널컬러 색 계산·품질·판정(퍼스널 탭 설계 2절, 임시 기준 pc-v1) */
class PersonalColorRulesTest {

	private static final double EPS = 0.5;

	@Nested
	@DisplayName("ColorMath: sRGB(D65) → CIELAB")
	class Math {

		@Test
		@DisplayName("흰색·검정·빨강·피부색을 알려진 값과 대조")
		void knownValues() {
			Lab white = ColorMath.toLab(new Rgb(255, 255, 255));
			assertThat(white.l()).isCloseTo(100, within(EPS));
			assertThat(white.a()).isCloseTo(0, within(EPS));
			assertThat(white.b()).isCloseTo(0, within(EPS));
			assertThat(ColorMath.toLab(new Rgb(0, 0, 0)).l()).isCloseTo(0, within(EPS));
			Lab red = ColorMath.toLab(new Rgb(255, 0, 0));
			assertThat(red.l()).isCloseTo(53.24, within(EPS));
			assertThat(red.a()).isCloseTo(80.09, within(EPS));
			assertThat(red.b()).isCloseTo(67.20, within(EPS));
			Lab skin = ColorMath.toLab(new Rgb(224, 172, 150));
			assertThat(skin.l()).isCloseTo(74.54, within(EPS));
			assertThat(skin.a()).isCloseTo(16.02, within(EPS));
			assertThat(skin.b()).isCloseTo(18.66, within(EPS));
		}

		@Test
		@DisplayName("ITA°·색상각·채도")
		void angles() {
			assertThat(ColorMath.ita(new Lab(60, 0, 20))).isCloseTo(26.57, within(0.01));
			assertThat(ColorMath.hue(new Lab(50, 10, 10))).isCloseTo(45, within(0.01));
			assertThat(ColorMath.hue(new Lab(50, 10, -10))).isCloseTo(315, within(0.01));
			assertThat(ColorMath.chroma(new Lab(50, 3, 4))).isCloseTo(5, within(0.01));
		}

		@Test
		@DisplayName("흰 종이 보정: 채널마다 255/기준값을 곱하고 255로 자른다")
		void whiteBalance() {
			assertThat(ColorMath.whiteBalance(new Rgb(120, 120, 125), new Rgb(240, 240, 250)))
					.isEqualTo(new Rgb(128, 128, 128));
			assertThat(ColorMath.whiteBalance(new Rgb(250, 10, 10), new Rgb(200, 255, 255)))
					.isEqualTo(new Rgb(255, 10, 10));
			assertThat(ColorMath.whiteBalance(new Rgb(10, 20, 30), new Rgb(0, 255, 255)))
					.as("기준값 0은 보정하지 않는다").isEqualTo(new Rgb(10, 20, 30));
		}
	}

	@Nested
	@DisplayName("ColorQualityRules")
	class Quality {

		private static List<Lab> frames(double l, int n) {
			return Collections.nCopies(n, new Lab(l, 10, 15));
		}

		private static Map<Region, List<Lab>> regions(List<Lab> left, List<Lab> right, List<Lab> forehead) {
			return Map.of(Region.LEFT_CHEEK, left, Region.RIGHT_CHEEK, right, Region.FOREHEAD, forehead);
		}

		@Test
		@DisplayName("정상이면 통과, 점수는 0~1")
		void ok() {
			ColorQualityRules.Quality q = ColorQualityRules.check(regions(frames(65, 10), frames(66, 10), frames(67, 10)));

			assertThat(q.passed()).isTrue();
			assertThat(q.issues()).isEmpty();
			assertThat(q.score()).isBetween(0.8, 1.0);
		}

		@Test
		@DisplayName("프레임 4개 이하 → TOO_FEW_FRAMES")
		void tooFew() {
			assertThat(ColorQualityRules.check(regions(frames(65, 4), frames(65, 10), frames(65, 10))).issues())
					.containsExactly("TOO_FEW_FRAMES");
		}

		@Test
		@DisplayName("프레임 사이 L* 표준편차가 크면 UNSTABLE")
		void unstable() {
			List<Lab> shaky = List.of(new Lab(55, 10, 15), new Lab(75, 10, 15), new Lab(55, 10, 15), new Lab(75, 10, 15),
					new Lab(55, 10, 15));
			assertThat(ColorQualityRules.check(regions(shaky, frames(65, 5), frames(65, 5))).issues())
					.contains("UNSTABLE");
		}

		@Test
		@DisplayName("좌우 볼 밝기 차이가 크면 UNEVEN_LIGHT, 너무 어둡거나 밝으면 TOO_DARK·TOO_BRIGHT")
		void lighting() {
			assertThat(ColorQualityRules.check(regions(frames(55, 5), frames(70, 5), frames(62, 5))).issues())
					.containsExactly("UNEVEN_LIGHT");
			assertThat(ColorQualityRules.check(regions(frames(20, 5), frames(20, 5), frames(20, 5))).issues())
					.containsExactly("TOO_DARK");
			ColorQualityRules.Quality bright = ColorQualityRules.check(regions(frames(95, 5), frames(95, 5), frames(95, 5)));
			assertThat(bright.issues()).containsExactly("TOO_BRIGHT");
			assertThat(bright.passed()).isFalse();
		}
	}

	@Nested
	@DisplayName("PersonalColorRules.judge")
	class Judge {

		@Test
		@DisplayName("웜·밝음 → 봄 웜, 쿨·깊음 → 겨울 쿨. 두 번째 후보는 경계에 더 가까운 축을 뒤집은 계절")
		void seasons() {
			// h 62(경계 55에서 7°, 0.7) · ITA 50(경계 41에서 9, 0.6) → 밝기 축이 더 가깝다 → 가을 웜
			PersonalColorRules.Judgment warm = PersonalColorRules.judge(62, 50, 1.0);
			assertThat(warm.candidates()).containsExactly(PersonalColor.SPRING_WARM, PersonalColor.AUTUMN_WARM);
			// h 48(0.7) · ITA 30(11, 0.73) → 색상각 축이 더 가깝다 → 가을 웜
			PersonalColorRules.Judgment cool = PersonalColorRules.judge(48, 30, 1.0);
			assertThat(cool.candidates()).containsExactly(PersonalColor.WINTER_COOL, PersonalColor.AUTUMN_WARM);
			assertThat(PersonalColorRules.judge(50, 60, 1.0).candidates().get(0)).isEqualTo(PersonalColor.SUMMER_COOL);
		}

		@Test
		@DisplayName("경계값: h 55는 웜, ITA 41은 밝음")
		void boundary() {
			assertThat(PersonalColorRules.judge(55, 41, 1.0).candidates().get(0)).isEqualTo(PersonalColor.SPRING_WARM);
			assertThat(PersonalColorRules.judge(54.9, 40.9, 1.0).candidates().get(0)).isEqualTo(PersonalColor.WINTER_COOL);
		}

		@Test
		@DisplayName("신뢰도 = min(품질, 경계 거리). 0.4 미만이면 순서를 정하지 않는다")
		void confidence() {
			PersonalColorRules.Judgment near = PersonalColorRules.judge(56, 60, 1.0);
			assertThat(near.confidence()).isCloseTo(0.1, within(0.001));
			assertThat(near.ordered()).isFalse();
			assertThat(near.candidates().get(1)).as("색상각 축이 더 가까워 뒤집는다").isEqualTo(PersonalColor.SUMMER_COOL);

			PersonalColorRules.Judgment far = PersonalColorRules.judge(70, 70, 0.9);
			assertThat(far.confidence()).isCloseTo(0.9, within(0.001));
			assertThat(far.ordered()).isTrue();
			assertThat(PersonalColorRules.judge(70, 70, 0.3).ordered()).isFalse();
		}

		@Test
		@DisplayName("estimate: 이마 0.5 · 볼 각 0.25로 합친 Lab에서 판정한다")
		void estimate() {
			List<ColorSample> samples = List.of(
					new ColorSample(Region.FOREHEAD, Collections.nCopies(6, new Rgb(224, 172, 150))),
					new ColorSample(Region.LEFT_CHEEK, Collections.nCopies(6, new Rgb(224, 172, 150))),
					new ColorSample(Region.RIGHT_CHEEK, Collections.nCopies(6, new Rgb(224, 172, 150))));

			PersonalColorRules.Estimate estimate = PersonalColorRules.estimate(samples, null);

			assertThat(estimate.lab().l()).isCloseTo(74.54, within(EPS));
			assertThat(estimate.hue()).isCloseTo(49.4, within(EPS));
			assertThat(estimate.ita()).isCloseTo(52.8, within(EPS));
			assertThat(estimate.quality().passed()).isTrue();
			assertThat(estimate.judgment().candidates().get(0)).isEqualTo(PersonalColor.SUMMER_COOL);
		}
	}

	@Nested
	@DisplayName("SeasonPalette")
	class Palette {

		@Test
		@DisplayName("계절마다 #RRGGBB 8개, 모두 기존 ShadeClassifier가 그 계절로 판정한다")
		void palettes() {
			Map<PersonalColor, ShadeSeason> seasons = Map.of(PersonalColor.SPRING_WARM, ShadeSeason.SPRING,
					PersonalColor.SUMMER_COOL, ShadeSeason.SUMMER, PersonalColor.AUTUMN_WARM, ShadeSeason.AUTUMN,
					PersonalColor.WINTER_COOL, ShadeSeason.WINTER);
			for (PersonalColor color : PersonalColor.values()) {
				List<String> hexes = SeasonPalette.of(color);
				assertThat(hexes).hasSize(8).allMatch(h -> h.matches(ShadeClassifier.HEX_REGEX));
				assertThat(hexes).allSatisfy(h -> assertThat(ShadeClassifier.classify(h).season())
						.as(h).isEqualTo(seasons.get(color)));
			}
		}
	}
}
