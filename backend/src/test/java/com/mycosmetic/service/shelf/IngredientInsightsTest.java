package com.mycosmetic.service.shelf;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.Arrays;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mycosmetic.dto.shelf.IngredientInsightsResponse;
import com.mycosmetic.dto.shelf.IngredientInsightsResponse.Insight;
import com.mycosmetic.entity.Verdict;
import com.mycosmetic.service.shelf.IngredientInsights.Entry;
import com.mycosmetic.service.shelf.IngredientInsights.Ingredient;

class IngredientInsightsTest {

	static Entry entry(Verdict verdict, String... names) {
		return new Entry(verdict, Arrays.stream(names).map(n -> new Ingredient(n, n)).toList());
	}

	@Test
	@DisplayName("안 맞음 제품에 자주 있고 맞음 제품에는 드문 성분을 의심 성분으로, 정제수처럼 어디에나 있는 성분은 뺀다")
	void suspectsAndFriendly() {
		IngredientInsightsResponse result = IngredientInsights.compute(List.of(
				entry(Verdict.BAD, "정제수", "향료", "알코올"),
				entry(Verdict.BAD, "정제수", "향료", "알코올", "향료"), // 한 제품 안 중복은 한 번
				entry(Verdict.BAD, "정제수", "향료"),
				entry(Verdict.GOOD, "정제수", "판테놀", "알코올"),
				entry(Verdict.GOOD, "정제수", "판테놀"),
				entry(Verdict.GOOD, "정제수", "판테놀"),
				entry(Verdict.GOOD, "정제수", "세라마이드"),
				entry(Verdict.NEUTRAL, "향료"), // 보통·평가 전은 세지 않는다
				entry(Verdict.UNRATED, "향료")));

		assertThat(result.ready()).isTrue();
		assertThat(result.badCount()).isEqualTo(3);
		assertThat(result.goodCount()).isEqualTo(4);
		assertThat(result.suspects()).containsExactly(new Insight("향료", 3, 0), new Insight("알코올", 2, 1));
		assertThat(result.friendly()).containsExactly(new Insight("판테놀", 0, 3));
		// 비교 토글용: 맞음·안 맞음 제품에 나온 모든 성분(보통·평가 전 제외), 이름순
		assertThat(result.rows()).containsExactly(new Insight("세라마이드", 0, 1), new Insight("알코올", 2, 1),
				new Insight("정제수", 3, 4), new Insight("판테놀", 0, 3), new Insight("향료", 3, 0));
	}

	@Test
	@DisplayName("안 맞음 2개, 맞음 1개 이상이 없으면 가려내지 않는다")
	void notEnough() {
		IngredientInsightsResponse onlyBad = IngredientInsights.compute(List.of(
				entry(Verdict.BAD, "정제수", "향료"), entry(Verdict.BAD, "정제수", "향료")));
		IngredientInsightsResponse oneBad = IngredientInsights.compute(List.of(
				entry(Verdict.BAD, "향료"), entry(Verdict.GOOD, "판테놀")));

		assertThat(onlyBad.ready()).isFalse();
		assertThat(onlyBad.suspects()).isEmpty();
		assertThat(oneBad.ready()).isFalse();
		assertThat(oneBad.friendly()).isEmpty();
		// 판정은 못 해도 비교 토글은 맞음 1·안 맞음 1부터 보여줄 수 있게 개수는 준다
		assertThat(oneBad.rows()).containsExactly(new Insight("판테놀", 0, 1), new Insight("향료", 1, 0));
	}
}
