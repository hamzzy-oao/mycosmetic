package com.mycosmetic.service.vanity;

import java.util.List;

import com.mycosmetic.entity.StepType;
import com.mycosmetic.service.routine.RoutineWarning;

/**
 * 아침·저녁 루틴 초안(퍼스널 탭 설계 3절 A).
 *
 * @param alternates 같은 단계에서 고르지 않은 제품("교대로 쓸 수 있어요")
 */
public record RoutineDraft(List<DraftStep> morning, List<DraftStep> evening, List<RoutineWarning> warnings,
		List<Gap> gaps, List<Alternate> alternates) {

	public RoutineDraft {
		morning = List.copyOf(morning);
		evening = List.copyOf(evening);
		warnings = List.copyOf(warnings);
		gaps = List.copyOf(gaps);
		alternates = List.copyOf(alternates);
	}

	public record DraftStep(Long shelfItemId, StepType stepType, boolean guessed) {
	}

	/**
	 * 꼭 필요한데 비어 있는 단계.
	 *
	 * @param role            CLEANSER | MOISTURIZER | SUNSCREEN
	 * @param level           지금은 ESSENTIAL만 쓴다(선택 단계는 알리지 않는다)
	 * @param ingredientHints 찾아볼 성분 안내
	 * @param contentId       사용법 콘텐츠(frontend content/tips.json의 id)
	 */
	public record Gap(String role, String level, String message, List<String> ingredientHints, String contentId) {

		public Gap {
			ingredientHints = List.copyOf(ingredientHints);
		}
	}

	public record Alternate(StepType stepType, List<Long> shelfItemIds) {

		public Alternate {
			shelfItemIds = List.copyOf(shelfItemIds);
		}
	}
}
