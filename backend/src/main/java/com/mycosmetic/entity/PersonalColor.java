package com.mycosmetic.entity;

/** 퍼스널컬러(no-ai-features 설계 E). 앞 단어가 계절, 뒤 단어가 웜/쿨 */
public enum PersonalColor {
	SPRING_WARM(true), SUMMER_COOL(false), AUTUMN_WARM(true), WINTER_COOL(false);

	private final boolean warm;

	PersonalColor(boolean warm) {
		this.warm = warm;
	}

	public boolean isWarm() {
		return warm;
	}
}
