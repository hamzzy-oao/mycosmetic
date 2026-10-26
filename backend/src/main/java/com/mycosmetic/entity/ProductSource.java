package com.mycosmetic.entity;

/** 제품 정보(브랜드·제품명)를 어디서 가져왔는지 */
public enum ProductSource {
	/** 브랜드 공식몰에서 모아 시드로 넣음 */
	SEED,
	/** 올리브영 링크에서 가져온 이름을 그대로 저장함 */
	LINK,
	/** 사용자가 직접 입력함(또는 가져온 이름을 고침) */
	USER
}
