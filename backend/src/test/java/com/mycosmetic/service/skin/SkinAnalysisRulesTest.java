package com.mycosmetic.service.skin;

import static com.mycosmetic.entity.SkinConcern.ACNE;
import static com.mycosmetic.entity.SkinConcern.DRYNESS;
import static com.mycosmetic.entity.SkinConcern.OILINESS;
import static com.mycosmetic.entity.SkinConcern.PIGMENTATION;
import static com.mycosmetic.entity.SkinConcern.PORES;
import static com.mycosmetic.entity.SkinConcern.SENSITIVITY;
import static com.mycosmetic.entity.SkinConcern.WRINKLES;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

import com.mycosmetic.entity.SkinConcern;
import com.mycosmetic.entity.SkinType;
import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.exception.ErrorCode;

/** 피부 분석 판정 규칙(퍼스널 탭 설계 1절 판정 규칙 표) */
class SkinAnalysisRulesTest {

	private static SkinAnalysisResult analyze(SelfType self, List<SkinConcern> concerns) {
		return SkinAnalysisRules.analyze(self, null, concerns, 0, 0);
	}

	@Nested
	@DisplayName("기본 타입")
	class Base {

		@Test
		@DisplayName("규칙 1: 지성 + 건조함 → 수부지")
		void oilyPlusDryness_isDehydratedOily() {
			SkinAnalysisResult result = analyze(SelfType.OILY, List.of(DRYNESS));

			assertThat(result.baseType()).isEqualTo(SkinType.DEHYDRATED_OILY);
			assertThat(result.reasons()).anyMatch(r -> r.contains("지성인데 건조함이 고민이라고 답했어요"));
		}

		@Test
		@DisplayName("규칙 1이 먼저: 지성 + 건조함 + 유분 → 수부지")
		void oilyPlusDrynessPlusOiliness_isDehydratedOily() {
			assertThat(analyze(SelfType.OILY, List.of(OILINESS, DRYNESS)).baseType())
					.isEqualTo(SkinType.DEHYDRATED_OILY);
		}

		@Test
		@DisplayName("규칙 2: 건성 + 유분 → 복합성")
		void dryPlusOiliness_isCombination() {
			SkinAnalysisResult result = analyze(SelfType.DRY, List.of(OILINESS));

			assertThat(result.baseType()).isEqualTo(SkinType.COMBINATION);
			assertThat(result.reasons()).anyMatch(r -> r.contains("건성인데 유분이 고민이라고 답했어요"));
		}

		@Test
		@DisplayName("규칙 3: 그 밖에는 고른 타입 그대로(고민이 없어도)")
		void selfTypeKept() {
			assertThat(analyze(SelfType.DRY, List.of(WRINKLES)).baseType()).isEqualTo(SkinType.DRY);
			assertThat(analyze(SelfType.OILY, List.of(PORES)).baseType()).isEqualTo(SkinType.OILY);
			assertThat(analyze(SelfType.COMBINATION, List.of()).baseType()).isEqualTo(SkinType.COMBINATION);
			assertThat(analyze(SelfType.COMBINATION, List.of(DRYNESS, OILINESS)).baseType())
					.isEqualTo(SkinType.COMBINATION);
			assertThat(analyze(SelfType.DRY, List.of()).reasons()).isNotEmpty();
		}

		@ParameterizedTest(name = "잘 모르겠어요 + {0} → {1}")
		@CsvSource({ "ALL_TIGHT, DRY", "CHEEK_TIGHT, COMBINATION", "TZONE_OILY, COMBINATION", "ALL_OILY, OILY" })
		@DisplayName("잘 모르겠어요: 한낮 문항으로 정한다")
		void unsure_usesMiddayFeel(MiddayFeel feel, SkinType expected) {
			assertThat(SkinAnalysisRules.analyze(SelfType.UNSURE, feel, List.of(), 0, 0).baseType())
					.isEqualTo(expected);
		}

		@Test
		@DisplayName("한낮 문항으로 정한 타입에도 규칙 1·2를 적용한다")
		void unsure_thenRules() {
			assertThat(SkinAnalysisRules.analyze(SelfType.UNSURE, MiddayFeel.ALL_OILY, List.of(DRYNESS), 0, 0)
					.baseType()).isEqualTo(SkinType.DEHYDRATED_OILY);
			assertThat(SkinAnalysisRules.analyze(SelfType.UNSURE, MiddayFeel.ALL_TIGHT, List.of(OILINESS), 0, 0)
					.baseType()).isEqualTo(SkinType.COMBINATION);
		}

		@Test
		@DisplayName("Q1을 직접 고르면 한낮 문항 답은 무시한다")
		void selfType_ignoresMiddayFeel() {
			assertThat(SkinAnalysisRules.analyze(SelfType.DRY, MiddayFeel.ALL_OILY, List.of(), 0, 0).baseType())
					.isEqualTo(SkinType.DRY);
		}
	}

	@Nested
	@DisplayName("태그")
	class Tags {

		@Test
		@DisplayName("민감함 → 민감성, 여드름 → 트러블성, 둘 다 가능")
		void tags() {
			assertThat(analyze(SelfType.DRY, List.of(SENSITIVITY)).tags()).containsExactly(SkinType.SENSITIVE);
			assertThat(analyze(SelfType.OILY, List.of(ACNE)).tags()).containsExactly(SkinType.ACNE_PRONE);
			assertThat(analyze(SelfType.COMBINATION, List.of(ACNE, SENSITIVITY)).tags())
					.containsExactly(SkinType.SENSITIVE, SkinType.ACNE_PRONE);
			assertThat(analyze(SelfType.DRY, List.of(PIGMENTATION)).tags()).isEmpty();
		}

		@Test
		@DisplayName("types()는 기본 타입 + 태그, 고민은 그대로 담는다")
		void typesAndConcerns() {
			SkinAnalysisResult result = analyze(SelfType.OILY, List.of(DRYNESS, PORES, ACNE));

			assertThat(result.types()).containsExactlyInAnyOrder(SkinType.DEHYDRATED_OILY, SkinType.ACNE_PRONE);
			assertThat(result.concerns()).containsExactly(DRYNESS, PORES, ACNE);
		}
	}

	@Nested
	@DisplayName("민감성 제안")
	class SuggestSensitive {

		@Test
		@DisplayName("안맞아요 2개 이상이고 비율 50% 이상이면 제안한다")
		void boundary() {
			assertThat(SkinAnalysisRules.analyze(SelfType.DRY, null, List.of(), 2, 2).suggestSensitive()).isTrue();
			assertThat(SkinAnalysisRules.analyze(SelfType.DRY, null, List.of(), 0, 2).suggestSensitive()).isTrue();
			assertThat(SkinAnalysisRules.analyze(SelfType.DRY, null, List.of(), 3, 2).suggestSensitive()).isFalse();
			assertThat(SkinAnalysisRules.analyze(SelfType.DRY, null, List.of(), 0, 1).suggestSensitive()).isFalse();
		}

		@Test
		@DisplayName("이미 민감성이면 제안하지 않는다")
		void alreadySensitive() {
			assertThat(SkinAnalysisRules.analyze(SelfType.DRY, null, List.of(SENSITIVITY), 0, 5).suggestSensitive())
					.isFalse();
		}
	}

	@Nested
	@DisplayName("입력 검사")
	class Invalid {

		private void expectInvalid(Runnable call) {
			assertThatThrownBy(call::run).isInstanceOfSatisfying(BusinessException.class,
					e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INVALID_SKIN_ANSWERS));
		}

		@Test
		@DisplayName("잘 모르겠어요인데 한낮 답 없음, 고민 4개, 고민 중복, Q1 없음")
		void invalid() {
			expectInvalid(() -> SkinAnalysisRules.analyze(SelfType.UNSURE, null, List.of(), 0, 0));
			expectInvalid(() -> analyze(SelfType.DRY, List.of(DRYNESS, PORES, ACNE, WRINKLES)));
			expectInvalid(() -> analyze(SelfType.DRY, List.of(PORES, PORES)));
			expectInvalid(() -> analyze(null, List.of()));
			expectInvalid(() -> analyze(SelfType.DRY, null));
		}
	}
}
