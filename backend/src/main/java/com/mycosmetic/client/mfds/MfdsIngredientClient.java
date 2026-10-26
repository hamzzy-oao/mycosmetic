package com.mycosmetic.client.mfds;

/**
 * 식약처 화장품 원료성분정보 API (설계 4.5). 테스트에서는 가짜 구현으로 바꿔 끼운다.
 */
public interface MfdsIngredientClient {

	/**
	 * 한 페이지를 가져온다.
	 *
	 * @param pageNo    1부터 시작
	 * @param numOfRows 페이지 크기
	 * @throws MfdsClientException 호출·응답 해석 실패, 서비스 키 없음
	 */
	MfdsPage fetchPage(int pageNo, int numOfRows);
}
