package com.mycosmetic.service.product;

import static org.assertj.core.api.Assertions.assertThat;

import java.time.Instant;
import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.test.util.ReflectionTestUtils;

import com.mycosmetic.entity.IngredientList;
import com.mycosmetic.entity.ListStatus;

/** 설계 3.5-3, 3.5-4 */
class ListVersionRulesTest {

	private static final Instant T0 = Instant.parse("2026-10-01T00:00:00Z");

	@Test
	void adminCorrectionBeatsCommunityVotes() {
		IngredientList community = list(1L, ListStatus.VERIFIED, 50, T0);
		IngredientList correction = list(2L, ListStatus.PENDING, 0, T0);
		correction.approveByAdmin();
		assertThat(ListVersionRules.representative(List.of(community, correction))).containsSame(correction);
		correction.clearAdminApproval();
		assertThat(ListVersionRules.representative(List.of(community, correction))).containsSame(community);
	}

	@Test
	@DisplayName("확인 1명은 PENDING, 2명은 VERIFIED")
	void statusByDistinctConfirmers() {
		assertThat(ListVersionRules.statusFor(0)).isEqualTo(ListStatus.PENDING);
		assertThat(ListVersionRules.statusFor(1)).isEqualTo(ListStatus.PENDING);
		assertThat(ListVersionRules.statusFor(2)).isEqualTo(ListStatus.VERIFIED);
		assertThat(ListVersionRules.statusFor(5)).isEqualTo(ListStatus.VERIFIED);
	}

	@Test
	@DisplayName("대표 버전: VERIFIED가 PENDING보다 confirm_count가 작아도 우선")
	void verifiedBeatsPending() {
		IngredientList pending = list(1L, ListStatus.PENDING, 9, T0.plusSeconds(100));
		IngredientList verified = list(2L, ListStatus.VERIFIED, 2, T0);

		assertThat(ListVersionRules.representative(List.of(pending, verified))).containsSame(verified);
	}

	@Test
	@DisplayName("대표 버전: confirm_count가 같으면 최신")
	void tieGoesToNewest() {
		IngredientList older = list(1L, ListStatus.VERIFIED, 3, T0);
		IngredientList newer = list(2L, ListStatus.VERIFIED, 3, T0.plusSeconds(60));
		IngredientList fewer = list(3L, ListStatus.VERIFIED, 2, T0.plusSeconds(120));
		// created_at까지 같으면 id가 큰 것
		IngredientList sameTimeSmallerId = list(4L, ListStatus.PENDING, 1, T0);
		IngredientList sameTimeBiggerId = list(5L, ListStatus.PENDING, 1, T0);

		assertThat(ListVersionRules.representative(List.of(newer, fewer, older))).containsSame(newer);
		assertThat(ListVersionRules.representative(List.of(sameTimeBiggerId, sameTimeSmallerId)))
				.containsSame(sameTimeBiggerId);
		// VERIFIED가 없으면 PENDING에서 같은 규칙
		IngredientList pendingMore = list(6L, ListStatus.PENDING, 1, T0);
		IngredientList pendingLess = list(7L, ListStatus.PENDING, 0, T0.plusSeconds(60));
		assertThat(ListVersionRules.representative(List.of(pendingLess, pendingMore))).containsSame(pendingMore);
	}

	@Test
	@DisplayName("버전이 없으면 empty")
	void emptyWhenNoLists() {
		assertThat(ListVersionRules.representative(List.of())).isEmpty();
	}

	private static IngredientList list(Long id, ListStatus status, int confirmCount, Instant createdAt) {
		IngredientList list = IngredientList.create(null, "fp" + id, null);
		ReflectionTestUtils.setField(list, "id", id);
		ReflectionTestUtils.setField(list, "status", status);
		ReflectionTestUtils.setField(list, "confirmCount", confirmCount);
		ReflectionTestUtils.setField(list, "createdAt", createdAt);
		return list;
	}
}
