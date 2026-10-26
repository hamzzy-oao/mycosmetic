package com.mycosmetic.client.ai;

/**
 * 전성분 사진에서 성분 목록을 읽는 외부 AI 호출. 테스트·E2E에서는 가짜 구현으로 바꿔 끼운다.
 */
public interface IngredientExtractionClient {

	/**
	 * @param image     사진 바이트(메모리에서만 다룬다)
	 * @param mediaType {@code "image/jpeg"} 또는 {@code "image/png"}
	 * @throws ExtractionClientException 호출이나 응답 해석에 실패했을 때
	 */
	ExtractionResult extract(byte[] image, String mediaType);
}
