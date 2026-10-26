package com.mycosmetic.service.product;

import java.util.Comparator;
import java.util.List;
import java.util.Optional;

import com.mycosmetic.entity.IngredientList;
import com.mycosmetic.entity.ListStatus;

/** 성분 버전 상태와 대표 버전 규칙 (설계 3.5-3, 3.5-4) */
public final class ListVersionRules {

	/** confirm_count가 크고, 같으면 created_at이 최신이고, 같으면 id가 큰 것이 앞선다 */
	private static final Comparator<IngredientList> PRIORITY = Comparator
			.comparingInt(IngredientList::getConfirmCount)
			.thenComparing(IngredientList::getCreatedAt, Comparator.nullsFirst(Comparator.naturalOrder()))
			.thenComparing(IngredientList::getId, Comparator.nullsFirst(Comparator.naturalOrder()));

	private ListVersionRules() {
	}

	/** 서로 다른 확인자가 {@link IngredientList#VERIFY_THRESHOLD}명 이상이면 VERIFIED */
	public static ListStatus statusFor(long distinctConfirmers) {
		return distinctConfirmers >= IngredientList.VERIFY_THRESHOLD ? ListStatus.VERIFIED : ListStatus.PENDING;
	}

	/** VERIFIED 중 우선순위가 가장 높은 버전. VERIFIED가 없으면 PENDING 중에서. 버전이 없으면 empty */
	public static Optional<IngredientList> representative(List<IngredientList> lists) {
		Optional<IngredientList> approved = lists.stream().filter(IngredientList::isAdminApproved)
				.max(Comparator.comparing(IngredientList::getId));
		if (approved.isPresent()) return approved;
		Optional<IngredientList> verified = lists.stream()
				.filter(IngredientList::isVerified)
				.max(PRIORITY);
		if (verified.isPresent()) {
			return verified;
		}
		return lists.stream().max(PRIORITY);
	}
}
