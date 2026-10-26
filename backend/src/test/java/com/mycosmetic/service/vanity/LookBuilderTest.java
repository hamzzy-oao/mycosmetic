package com.mycosmetic.service.vanity;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mycosmetic.entity.MakeupType;
import com.mycosmetic.entity.PersonalColor;
import com.mycosmetic.entity.Verdict;
import com.mycosmetic.service.color.SeasonPalette;
import com.mycosmetic.service.shade.ShadeClassifier;
import com.mycosmetic.service.vanity.LookBuilder.Look;
import com.mycosmetic.service.vanity.LookBuilder.LookItem;
import com.mycosmetic.service.vanity.LookBuilder.Looks;

/** 보유 색조 조합(퍼스널 탭 설계 3절 B) */
class LookBuilderTest {

	// 부드러운 웜: 크로마 낮음 / 선명한 웜: 크로마 높음 / 쿨
	static final String SOFT_CORAL_LIP = "#D9A08C"; // 크로마 0.30
	static final String SOFT_PEACH_CHEEK = "#E8B9A0"; // 0.29
	static final String SOFT_BROWN_EYE = "#A68A73"; // 0.20
	static final String VIVID_ORANGE_LIP = "#F2541B"; // 0.84
	static final String COOL_PINK_CHEEK = "#E8A0BF"; // 쿨

	private static LookItem item(long id, MakeupType type, String hex) {
		return new LookItem(id, type, hex, Verdict.UNRATED);
	}

	private static Look kind(Looks looks, String kind) {
		return looks.looks().stream().filter(l -> l.kind().equals(kind)).findFirst().orElse(null);
	}

	@Test
	@DisplayName("크로마 기준이 0.45이고, 예시 색이 의도한 선명도·톤이다")
	void fixtures() {
		assertThat(ShadeClassifier.chromaOf(SOFT_CORAL_LIP)).isLessThan(LookBuilder.VIVID_CHROMA);
		assertThat(ShadeClassifier.chromaOf(VIVID_ORANGE_LIP)).isGreaterThan(LookBuilder.VIVID_CHROMA);
		assertThat(ShadeClassifier.classify(COOL_PINK_CHEEK).tone().name()).isEqualTo("COOL");
		assertThat(LookBuilder.VIVID_CHROMA).isEqualTo(0.45);
	}

	@Test
	@DisplayName("데일리: 모두 부드러움 / 포인트: 정확히 하나만 선명")
	void dailyAndPoint() {
		Looks looks = LookBuilder.build(List.of(item(1, MakeupType.LIP, SOFT_CORAL_LIP),
				item(2, MakeupType.CHEEK, SOFT_PEACH_CHEEK), item(3, MakeupType.LIP, VIVID_ORANGE_LIP)),
				PersonalColor.AUTUMN_WARM);

		assertThat(kind(looks, "DAILY").shelfItemIds()).containsExactly(1L, 2L);
		assertThat(kind(looks, "POINT").shelfItemIds()).containsExactly(3L, 2L);
		assertThat(looks.needsPersonalColor()).isFalse();
	}

	@Test
	@DisplayName("톤온톤: 모든 쌍의 색상각 차이가 30° 이내(원형 차이, 355°와 10°는 15°)")
	void toneOnTone() {
		assertThat(LookBuilder.hueDistance(355, 10)).isEqualTo(15);
		Looks looks = LookBuilder.build(List.of(item(1, MakeupType.LIP, SOFT_CORAL_LIP),
				item(2, MakeupType.CHEEK, SOFT_PEACH_CHEEK)), PersonalColor.SPRING_WARM);
		assertThat(kind(looks, "TONE_ON_TONE")).isNotNull();

		Looks mixed = LookBuilder.build(List.of(item(1, MakeupType.LIP, SOFT_CORAL_LIP),
				item(2, MakeupType.CHEEK, COOL_PINK_CHEEK)), PersonalColor.SPRING_WARM);
		assertThat(kind(mixed, "TONE_ON_TONE")).isNull();
	}

	@Test
	@DisplayName("점수: 내 톤과 웜/쿨 같음 +2, 계절까지 같음 +1, 잘맞아요 +1, 웜·쿨 섞임 −2")
	void score() {
		LookItem lip = new LookItem(1L, MakeupType.LIP, SOFT_CORAL_LIP, Verdict.GOOD);
		LookItem cheek = item(2, MakeupType.CHEEK, COOL_PINK_CHEEK);
		String lipSeason = ShadeClassifier.classify(SOFT_CORAL_LIP).season().name();
		PersonalColor mine = lipSeason.equals("SPRING") ? PersonalColor.SPRING_WARM : PersonalColor.AUTUMN_WARM;

		Look daily = kind(LookBuilder.build(List.of(lip, cheek), mine), "DAILY");

		// 립: 톤 +2, 계절 +1, GOOD +1 = 4 / 블러셔(쿨): 0 / 섞임 −2 → 2
		assertThat(daily.score()).isEqualTo(2);
		assertThat(daily.reasons()).anyMatch(r -> r.contains("섞여"));
	}

	@Test
	@DisplayName("종류마다 최고점 하나, 동점이면 잘맞아요 많은 쪽 → id 작은 쪽")
	void tieBreak() {
		Looks looks = LookBuilder.build(List.of(item(5, MakeupType.LIP, SOFT_CORAL_LIP),
				item(2, MakeupType.LIP, SOFT_CORAL_LIP), new LookItem(9L, MakeupType.CHEEK, SOFT_PEACH_CHEEK, Verdict.GOOD)),
				null);

		assertThat(kind(looks, "DAILY").shelfItemIds()).containsExactly(2L, 9L);
		assertThat(looks.looks().stream().map(Look::kind)).doesNotHaveDuplicates();
	}

	@Test
	@DisplayName("퍼스널컬러가 없으면 일치 점수 없이 만들고 needsPersonalColor, 색 범위 추천은 없다")
	void noPersonalColor() {
		Looks looks = LookBuilder.build(List.of(item(1, MakeupType.LIP, SOFT_CORAL_LIP),
				item(2, MakeupType.CHEEK, SOFT_PEACH_CHEEK)), null);

		assertThat(looks.needsPersonalColor()).isTrue();
		assertThat(kind(looks, "DAILY").score()).isZero();
		assertThat(looks.shadeGaps()).isEmpty();
	}

	@Test
	@DisplayName("종류가 2개 미만이면 조합이 없고, 베이스는 조합에 쓰지 않는다")
	void lessThanTwoKinds_empty() {
		assertThat(LookBuilder.build(List.of(item(1, MakeupType.LIP, SOFT_CORAL_LIP),
				item(2, MakeupType.LIP, VIVID_ORANGE_LIP), item(3, MakeupType.LIP, SOFT_CORAL_LIP)), null).looks()).isEmpty();
		assertThat(LookBuilder.build(List.of(item(1, MakeupType.LIP, SOFT_CORAL_LIP),
				item(2, MakeupType.BASE, "#E8C9A8")), null).looks()).isEmpty();
	}

	@Test
	@DisplayName("색 범위 추천: 내 계절에서 보유 0개인 종류마다 팔레트 색")
	void shadeGaps() {
		Looks looks = LookBuilder.build(List.of(item(1, MakeupType.LIP, SOFT_CORAL_LIP)), PersonalColor.AUTUMN_WARM);

		assertThat(looks.shadeGaps()).extracting(LookBuilder.ShadeGap::makeupType)
				.containsExactly(MakeupType.CHEEK, MakeupType.EYE);
		assertThat(looks.shadeGaps().get(0).season()).isEqualTo(PersonalColor.AUTUMN_WARM);
		assertThat(looks.shadeGaps().get(0).hexes()).isEqualTo(SeasonPalette.of(PersonalColor.AUTUMN_WARM));
	}
}
