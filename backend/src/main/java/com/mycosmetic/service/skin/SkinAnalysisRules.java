package com.mycosmetic.service.skin;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

import com.mycosmetic.entity.SkinConcern;
import com.mycosmetic.entity.SkinType;
import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.exception.ErrorCode;

/**
 * 피부 분석 판정(퍼스널 탭 설계 1절 판정 규칙 표). 순수 함수라 DB 없이 단위 테스트한다.
 * <ol>
 * <li>지성 + 건조함 → 수부지</li>
 * <li>건성 + 유분 → 복합성</li>
 * <li>그 밖에는 Q1(잘 모르겠어요면 Q1-1)에서 정한 타입 그대로</li>
 * <li>민감함 → 민감성 태그, 여드름 → 트러블성 태그</li>
 * <li>민감성 태그가 없고 안맞아요 2개 이상·비율 50% 이상이면 민감성 추가를 제안(자동 반영 안 함)</li>
 * </ol>
 */
public final class SkinAnalysisRules {

	public static final String RULE_VERSION = "skin-v1";
	public static final int MAX_CONCERNS = 3;
	static final int SENSITIVE_MIN_BAD = 2;
	static final double SENSITIVE_BAD_RATIO = 0.5;

	public static final Map<SkinType, String> TYPE_LABELS = Map.of(SkinType.DRY, "건성", SkinType.OILY, "지성",
			SkinType.COMBINATION, "복합성", SkinType.DEHYDRATED_OILY, "수부지", SkinType.SENSITIVE, "민감성",
			SkinType.ACNE_PRONE, "트러블성");

	public static final Map<SkinConcern, String> CONCERN_LABELS = Map.of(SkinConcern.DRYNESS, "건조함",
			SkinConcern.OILINESS, "유분", SkinConcern.PORES, "모공", SkinConcern.WRINKLES, "주름",
			SkinConcern.PIGMENTATION, "색소침착", SkinConcern.SENSITIVITY, "민감함", SkinConcern.ACNE, "여드름");

	private SkinAnalysisRules() {
	}

	/**
	 * @param middayFeel selfType이 UNSURE일 때만 쓴다(아니면 무시)
	 * @param concerns   0~3개, 중복 없음(중복을 잡으려고 List로 받는다)
	 * @param goodCount  내 화장대의 잘맞아요 수
	 * @param badCount   내 화장대의 안맞아요 수
	 */
	public static SkinAnalysisResult analyze(SelfType selfType, MiddayFeel middayFeel, List<SkinConcern> concerns,
			int goodCount, int badCount) {
		validate(selfType, middayFeel, concerns);
		Set<SkinConcern> concernSet = concerns.isEmpty() ? EnumSet.noneOf(SkinConcern.class) : EnumSet.copyOf(concerns);
		List<String> reasons = new ArrayList<>();

		SkinType base;
		if (selfType == SelfType.UNSURE) {
			base = middayFeel.type();
			reasons.add("보습제 없이 한낮에 " + middayText(middayFeel) + "고 답해서 " + TYPE_LABELS.get(base)
					+ "으로 봤어요.");
		} else {
			base = switch (selfType) {
			case DRY -> SkinType.DRY;
			case OILY -> SkinType.OILY;
			case COMBINATION -> SkinType.COMBINATION;
			case UNSURE -> throw new IllegalStateException();
			};
		}

		if (base == SkinType.OILY && concernSet.contains(SkinConcern.DRYNESS)) {
			base = SkinType.DEHYDRATED_OILY;
			reasons.add("지성인데 건조함이 고민이라고 답했어요. 겉은 번들거리고 속은 건조한 수분 부족형 지성(수부지)으로 봤어요.");
		} else if (base == SkinType.DRY && concernSet.contains(SkinConcern.OILINESS)) {
			base = SkinType.COMBINATION;
			reasons.add("건성인데 유분이 고민이라고 답했어요. 볼은 건조하고 T존은 번들거리는 복합성으로 봤어요.");
		} else if (selfType != SelfType.UNSURE) {
			reasons.add("본인이 고른 " + TYPE_LABELS.get(base) + " 피부로 정했어요.");
		}

		Set<SkinType> tags = EnumSet.noneOf(SkinType.class);
		if (concernSet.contains(SkinConcern.SENSITIVITY)) {
			tags.add(SkinType.SENSITIVE);
			reasons.add("민감함이 고민이라고 답해서 민감성을 붙였어요.");
		}
		if (concernSet.contains(SkinConcern.ACNE)) {
			tags.add(SkinType.ACNE_PRONE);
			reasons.add("여드름이 고민이라고 답해서 트러블성을 붙였어요.");
		}

		boolean suggestSensitive = !tags.contains(SkinType.SENSITIVE) && badCount >= SENSITIVE_MIN_BAD
				&& badCount >= (goodCount + badCount) * SENSITIVE_BAD_RATIO;
		return new SkinAnalysisResult(base, tags, concernSet, reasons, suggestSensitive);
	}

	private static void validate(SelfType selfType, MiddayFeel middayFeel, List<SkinConcern> concerns) {
		if (selfType == null || concerns == null || concerns.size() > MAX_CONCERNS
				|| concerns.stream().anyMatch(c -> c == null)
				|| concerns.stream().distinct().count() != concerns.size()
				|| (selfType == SelfType.UNSURE && middayFeel == null)) {
			throw new BusinessException(ErrorCode.INVALID_SKIN_ANSWERS);
		}
	}

	private static String middayText(MiddayFeel feel) {
		return switch (feel) {
		case ALL_TIGHT -> "얼굴 전체가 당기고 건조하다";
		case CHEEK_TIGHT -> "볼은 당기고 T존은 괜찮다";
		case TZONE_OILY -> "볼은 괜찮고 T존이 번들거린다";
		case ALL_OILY -> "얼굴 전체가 번들거린다";
		};
	}
}
