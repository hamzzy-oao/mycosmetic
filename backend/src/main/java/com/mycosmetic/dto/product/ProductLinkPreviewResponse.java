package com.mycosmetic.dto.product;

import java.util.List;

import com.mycosmetic.dto.ingredient.IngredientSection;

/**
 * 상품 상세 링크에서 가져온 미리 채우기 정보.
 *
 * @param name               제품명(쇼핑몰 표기 그대로, 브랜드·용량이 섞여 있을 수 있음)
 * @param imageUrl           대표 사진 주소. 지원하는 사이트의 이미지 서버 주소가 아니면 null
 * @param sourceUrl          정리한 상세 페이지 주소(추적용 파라미터 제거)
 * @param notice             가져온 항목·못 가져온 항목 안내
 * @param ingredientSections 전성분을 옵션(호수·구성품)별로 나눠 사전과 맞춘 결과. 못 가져왔거나 꺼져 있으면 빈 목록
 * @param volume             용량. 모르면 null
 * @param paoMonths          개봉 후 사용기간(개월). 모르면 null
 */
public record ProductLinkPreviewResponse(String name, String imageUrl, String sourceUrl, String notice,
		List<IngredientSection> ingredientSections, String volume, Integer paoMonths) {

	public ProductLinkPreviewResponse {
		ingredientSections = ingredientSections == null ? List.of() : List.copyOf(ingredientSections);
	}
}
