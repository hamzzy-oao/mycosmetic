package com.mycosmetic.dto.vanity;

import java.util.List;

import com.mycosmetic.entity.Category;
import com.mycosmetic.entity.MakeupType;
import com.mycosmetic.entity.StepType;
import com.mycosmetic.entity.Verdict;
import com.mycosmetic.service.routine.RoutineWarning;
import com.mycosmetic.service.vanity.LookBuilder.Look;
import com.mycosmetic.service.vanity.LookBuilder.ShadeGap;
import com.mycosmetic.service.vanity.RoutineDraft.Alternate;
import com.mycosmetic.service.vanity.RoutineDraft.DraftStep;
import com.mycosmetic.service.vanity.RoutineDraft.Gap;

/**
 * GET /api/vanity/analysis(퍼스널 탭 설계 3절). 저장하지 않고 요청마다 계산한다.
 *
 * @param warnings   초안에 기존 루틴 충돌 규칙을 적용한 경고
 * @param gaps       꼭 필요한데 비어 있는 단계
 * @param alternates 같은 단계에서 고르지 않은 제품(교대 후보)
 * @param needsType  이름으로 단계·색조 종류를 알 수 없어 사용자가 골라야 하는 항목
 * @param items      분석에 쓴 내 화장대 항목(화면이 이름·색을 그린다)
 * @param looks              색조 조합(데일리·포인트·톤온톤, 종류마다 하나)
 * @param needsPersonalColor 퍼스널컬러가 없어 톤 일치 점수 없이 만들었는지
 * @param shadeGaps          내 계절에서 보유 0개인 색조 종류의 색 범위
 */
public record VanityAnalysisResponse(Draft routineDraft, List<RoutineWarning> warnings, List<Gap> gaps,
		List<Alternate> alternates, List<Long> needsType, List<Item> items, List<Look> looks,
		boolean needsPersonalColor, List<ShadeGap> shadeGaps) {

	public record Draft(List<DraftStep> morning, List<DraftStep> evening) {
	}

	/**
	 * @param guessed 단계·색조 종류를 이름으로 추정했으면 true
	 * @param shadeHex 색조 항목의 내 호수 색(없으면 null)
	 */
	public record Item(Long shelfItemId, String brand, String name, Category category, Verdict verdict,
			StepType stepType, MakeupType makeupType, boolean guessed, String shadeHex) {
	}
}
