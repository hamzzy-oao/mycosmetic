package com.mycosmetic.client.mfds;

import java.util.List;

/** 식약처 API 한 페이지. totalCount는 전체 건수 */
public record MfdsPage(int totalCount, List<MfdsIngredientRow> rows) {

	public MfdsPage {
		rows = List.copyOf(rows);
	}
}
