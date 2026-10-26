package com.mycosmetic.client.productpage;

/** 상품 상세 페이지 호출 실패. 원인 예외는 붙이지 않는다(요청 주소가 메시지에 섞이지 않게) */
public class ProductPageClientException extends RuntimeException {

	public ProductPageClientException(String message) {
		super(message);
	}
}
