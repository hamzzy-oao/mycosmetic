package com.mycosmetic.client.productpage;

/**
 * 쇼핑몰 상품 상세 페이지 HTML을 가져온다(제품명·사진 미리 채우기용). 테스트에서는 가짜 구현으로 바꿔 끼운다.
 * 주소는 호출하는 쪽이 넘기지 않는다. 상품 번호만 받아 정해진 주소로만 요청한다(SSRF 방지).
 */
public interface ProductPageClient {

	/**
	 * 올리브영 상품 상세 페이지 HTML.
	 *
	 * @param goodsNo 검증된 상품 번호(예: A000000184348)
	 * @throws ProductPageClientException 호출 실패, 오류 응답, 빈 응답
	 */
	String fetchOliveYoungDetail(String goodsNo);

	/**
	 * 올리브영 상품정보 제공고시 JSON(전성분·용량·사용기한).
	 *
	 * @param goodsNo      검증된 상품 번호
	 * @param standardCode 상세 페이지에서 찾은 옵션 바코드(숫자만)
	 * @throws ProductPageClientException 호출 실패, 오류 응답, 빈 응답
	 */
	default String fetchOliveYoungArticle(String goodsNo, String standardCode) {
		throw new ProductPageClientException("상품정보 제공고시 조회 미지원");
	}
}
