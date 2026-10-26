package com.mycosmetic.client.mfds;

/**
 * 식약처 API 호출 실패. 메시지에 서비스 키·요청 주소를 넣지 않는다(원인 예외도 붙이지 않는다. 원인 메시지에 주소가 들어 있다).
 */
public class MfdsClientException extends RuntimeException {

	public MfdsClientException(String message) {
		super(message);
	}
}
