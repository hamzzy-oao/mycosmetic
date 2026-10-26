package com.mycosmetic.service.skin;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.util.EnumSet;
import java.util.Set;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mycosmetic.entity.SkinType;
import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.exception.ErrorCode;

/** 기본 타입 1개 + 태그 규칙(퍼스널 탭 설계 1절) */
class SkinTypeRulesTest {

	@Test
	@DisplayName("빈 집합은 허용한다(아직 안 정함)")
	void empty_ok() {
		SkinTypeRules.validate(Set.of());
	}

	@Test
	@DisplayName("기본 타입 1개 + 민감성·트러블성 태그는 허용한다")
	void oneBasePlusTags_ok() {
		SkinTypeRules.validate(EnumSet.of(SkinType.COMBINATION, SkinType.SENSITIVE, SkinType.ACNE_PRONE));
		SkinTypeRules.validate(Set.of(SkinType.DEHYDRATED_OILY));
	}

	@Test
	@DisplayName("태그만 있으면 INVALID_SKIN_TYPES")
	void tagsOnly_rejected() {
		assertThatThrownBy(() -> SkinTypeRules.validate(Set.of(SkinType.SENSITIVE)))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INVALID_SKIN_TYPES));
	}

	@Test
	@DisplayName("기본 타입이 2개면 INVALID_SKIN_TYPES")
	void twoBases_rejected() {
		assertThatThrownBy(() -> SkinTypeRules.validate(EnumSet.of(SkinType.DRY, SkinType.OILY)))
				.isInstanceOfSatisfying(BusinessException.class,
						e -> assertThat(e.errorCode()).isEqualTo(ErrorCode.INVALID_SKIN_TYPES));
	}

	@Test
	@DisplayName("baseOf: 기본 타입을 꺼내고, 없으면 null")
	void baseOf() {
		assertThat(SkinTypeRules.baseOf(EnumSet.of(SkinType.OILY, SkinType.SENSITIVE))).isEqualTo(SkinType.OILY);
		assertThat(SkinTypeRules.baseOf(Set.of())).isNull();
		assertThat(SkinTypeRules.baseOf(Set.of(SkinType.ACNE_PRONE))).isNull();
	}
}
