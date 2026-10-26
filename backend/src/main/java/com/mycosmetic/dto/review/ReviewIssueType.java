package com.mycosmetic.dto.review;

/** 점검함 문제 종류. 선언 순서가 화면 탭·목록 순서다 */
public enum ReviewIssueType {
	/** 사용자가 직접 입력해 아직 관리자가 확인하지 않은 제품(2026-10-26). "확인 완료"로 없앤다 */
	NEW_USER_PRODUCT,
	/** 제품명에 브랜드·용량·판매 문구가 있음(올리브영식 표기 규칙) */
	NAME_FORMAT,
	/** 같은 브랜드를 다르게 적음(공백·대소문자만 다름) */
	BRAND_VARIANT,
	/** 같은 브랜드에 이름이 거의 같은 제품이 있음 */
	DUPLICATE_SUSPECT,
	/** 띄어쓰기만 고치면 사전에 맞는 성분 줄이 있음 */
	INGREDIENT_SPACING,
	/** 성분이 아닌 글(사용법 등)이 섞였거나 성분이 너무 적음 */
	INGREDIENT_NOT_LIST,
	/** 사전에 안 맞는 성분 줄이 많음 */
	INGREDIENT_UNMATCHED
}
