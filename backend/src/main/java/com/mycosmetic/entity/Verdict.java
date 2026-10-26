package com.mycosmetic.entity;

/**
 * 내 화장대 항목 평가. NEUTRAL(보통)은 더 이상 고를 수 없다(2026-10-24). 저장된 값은 V16에서 UNRATED로 바꿨고,
 * 예전 규칙 코드가 그대로 돌도록 값만 남겨 둔다. 새로 저장하려 하면 INVALID_REQUEST
 */
public enum Verdict {
	GOOD, BAD, NEUTRAL, UNRATED
}
