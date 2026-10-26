package com.mycosmetic.service.shade;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.junit.jupiter.params.provider.ValueSource;

import com.mycosmetic.dto.shelf.ShadeSeason;
import com.mycosmetic.dto.shelf.ShadeTone;
import com.mycosmetic.entity.PersonalColor;
import com.mycosmetic.service.shade.ShadeClassifier.Shade;

/** 색조 웜/쿨·계절 판정(no-ai-features 설계 E). 순수 단위 테스트 */
class ShadeClassifierTest {

	@ParameterizedTest(name = "{0} → {1}")
	@CsvSource({
			"#CC3300, WARM", // 정확히 15°
			"#CC3200, COOL", // 14.7°
			"#FFFF00, WARM", // 정확히 60°
			"#FEFF00, NEUTRAL", // 60.2°
			"#FF00FF, COOL", // 정확히 300°
			"#FE00FF, NEUTRAL", // 299.8°
			"#FF0000, COOL", // 0°
			"#00FF00, NEUTRAL", // 초록 120°
			"#0000FF, NEUTRAL" // 파랑 240°
	})
	@DisplayName("색상각 경계: 15~60°는 웜, 300° 이상·15° 미만은 쿨, 그 사이는 중립")
	void tone_hueBoundaries(String hex, ShadeTone tone) {
		assertThat(ShadeClassifier.classify(hex).tone()).isEqualTo(tone);
	}

	@ParameterizedTest(name = "{0}")
	@ValueSource(strings = { "#808080", "#000000", "#FFFFFF", "#8B7D7B", "#806C6C" })
	@DisplayName("크로마 0.08 미만(회색·검정·흰색에 가까움)은 NEUTRAL이고 계절이 없다")
	void greys_neutral(String hex) {
		assertThat(ShadeClassifier.classify(hex)).isEqualTo(new Shade(ShadeTone.NEUTRAL, null));
	}

	@Test
	@DisplayName("크로마 경계: 20/255는 회색, 21/255는 색으로 본다")
	void chroma_boundary() {
		assertThat(ShadeClassifier.classify("#806C6C").tone()).isEqualTo(ShadeTone.NEUTRAL);
		assertThat(ShadeClassifier.classify("#806B6B").tone()).isEqualTo(ShadeTone.COOL);
	}

	@Test
	@DisplayName("웜/쿨 밖 색상(초록·파랑)은 계절이 없다")
	void neutralHue_noSeason() {
		assertThat(ShadeClassifier.classify("#00FF00").season()).isNull();
	}

	@ParameterizedTest(name = "{0} → {1} {2}")
	@CsvSource({
			"#FF7F50, WARM, SPRING", // 코랄: 밝고 선명
			"#8B4513, WARM, AUTUMN", // 브라운: 어둡고 부드러움
			"#FFB6C1, COOL, SUMMER", // 연분홍: 밝고 부드러움
			"#FF00FF, COOL, WINTER", // 푸시아: 선명
			"#FF0000, COOL, WINTER",
			"#AA5500, WARM, SPRING", // 3·max - min = 510 (경계)
			"#A95400, WARM, AUTUMN", // 507
			"#FF55AA, COOL, SUMMER", // 3·min = max (경계)
			"#FF54AA, COOL, WINTER" // 3·min < max
	})
	@DisplayName("계절: 웜은 밝고 선명하면 봄·아니면 가을, 쿨은 밝기가 크로마 이상이면 여름·아니면 겨울")
	void seasons(String hex, ShadeTone tone, ShadeSeason season) {
		assertThat(ShadeClassifier.classify(hex)).isEqualTo(new Shade(tone, season));
	}

	@Test
	@DisplayName("소문자 hex도 받고 normalize는 대문자로 바꾼다")
	void lowercase_ok() {
		assertThat(ShadeClassifier.classify("#ff7f50")).isEqualTo(ShadeClassifier.classify("#FF7F50"));
		assertThat(ShadeClassifier.normalize("#ff7f50")).isEqualTo("#FF7F50");
	}

	@ParameterizedTest(name = "\"{0}\"")
	@NullAndEmptySource
	@ValueSource(strings = { "#FFF", "FF0000", "#GG0000", "#FF00000", " #FF0000", "#FF000", "rgb(0,0,0)" })
	@DisplayName("#RRGGBB가 아니면 IllegalArgumentException")
	void invalidHex_rejected(String hex) {
		assertThatThrownBy(() -> ShadeClassifier.classify(hex)).isInstanceOf(IllegalArgumentException.class);
		assertThatThrownBy(() -> ShadeClassifier.normalize(hex)).isInstanceOf(IllegalArgumentException.class);
	}

	@Test
	@DisplayName("맞음 = 웜/쿨이 같다. 계절은 보지 않고 중립은 누구와도 맞지 않는다")
	void matches_byTone() {
		assertThat(ShadeClassifier.matches("#FF7F50", PersonalColor.SPRING_WARM)).isTrue();
		assertThat(ShadeClassifier.matches("#FF7F50", PersonalColor.AUTUMN_WARM)).isTrue();
		assertThat(ShadeClassifier.matches("#FF7F50", PersonalColor.SUMMER_COOL)).isFalse();
		assertThat(ShadeClassifier.matches("#FFB6C1", PersonalColor.WINTER_COOL)).isTrue();
		assertThat(ShadeClassifier.matches("#FFB6C1", PersonalColor.SPRING_WARM)).isFalse();
		assertThat(ShadeClassifier.matches("#808080", PersonalColor.SPRING_WARM)).isFalse();
		assertThat(ShadeClassifier.matches("#808080", PersonalColor.WINTER_COOL)).isFalse();
		assertThat(ShadeClassifier.matches("#00FF00", PersonalColor.SUMMER_COOL)).isFalse();
	}

	@Test
	@DisplayName("색조나 퍼스널컬러가 없으면 판정하지 않는다(null)")
	void matches_missing_null() {
		assertThat(ShadeClassifier.matches(null, PersonalColor.SPRING_WARM)).isNull();
		assertThat(ShadeClassifier.matches("#FF7F50", null)).isNull();
	}

	@Test
	@DisplayName("퍼스널컬러의 웜/쿨")
	void personalColor_warm() {
		assertThat(PersonalColor.SPRING_WARM.isWarm()).isTrue();
		assertThat(PersonalColor.AUTUMN_WARM.isWarm()).isTrue();
		assertThat(PersonalColor.SUMMER_COOL.isWarm()).isFalse();
		assertThat(PersonalColor.WINTER_COOL.isWarm()).isFalse();
	}
}
