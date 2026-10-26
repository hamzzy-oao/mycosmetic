package com.mycosmetic.entity;

/**
 * 피부 타입. 기본 타입(건성·지성·복합성·수부지) 하나와 태그(민감성·트러블성)를 한 집합에 담는다(퍼스널 탭 설계 1절).
 * 선언 순서가 응답 순서다.
 */
public enum SkinType {
	SENSITIVE(false), ACNE_PRONE(false), DRY(true), OILY(true), COMBINATION(true),
	/** 수부지(수분 부족형 지성) */
	DEHYDRATED_OILY(true);

	private final boolean base;

	SkinType(boolean base) {
		this.base = base;
	}

	/** 기본 타입이면 true, 태그(민감성·트러블성)면 false */
	public boolean isBase() {
		return base;
	}
}
