package com.mycosmetic.service.skin;

import java.util.Set;

import com.mycosmetic.entity.SkinType;
import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.exception.ErrorCode;

/** 피부 타입 집합 규칙(퍼스널 탭 설계 1절). 순수 함수라 DB 없이 단위 테스트한다 */
public final class SkinTypeRules {

	private SkinTypeRules() {
	}

	/** 빈 집합은 허용한다(아직 안 정함). 아니면 기본 타입이 정확히 1개여야 한다(아니면 INVALID_SKIN_TYPES) */
	public static void validate(Set<SkinType> types) {
		if (types.isEmpty()) {
			return;
		}
		if (types.stream().filter(SkinType::isBase).count() != 1) {
			throw new BusinessException(ErrorCode.INVALID_SKIN_TYPES);
		}
	}

	/** 기본 타입. 없으면 null */
	public static SkinType baseOf(Set<SkinType> types) {
		return types.stream().filter(SkinType::isBase).findFirst().orElse(null);
	}
}
