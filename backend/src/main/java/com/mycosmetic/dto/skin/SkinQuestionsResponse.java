package com.mycosmetic.dto.skin;

import java.util.List;

/**
 * 피부 분석 문항(퍼스널 탭 설계 1절).
 *
 * @param intro 첫 화면 안내 문구
 */
public record SkinQuestionsResponse(String ruleVersion, String intro, List<Question> questions) {

	/**
	 * @param id        요청 필드 이름(selfType, middayFeel, concerns)
	 * @param optional  답하지 않아도 되는지
	 * @param maxSelect 고를 수 있는 최대 개수(하나만 고르면 1)
	 * @param showWhen  이 문항을 보여주는 조건(예: "selfType=UNSURE"), 늘 보여주면 null
	 */
	public record Question(String id, String text, boolean optional, int maxSelect, String showWhen,
			List<Option> options) {
	}

	public record Option(String code, String label) {
	}
}
