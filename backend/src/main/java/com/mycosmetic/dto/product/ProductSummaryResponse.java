package com.mycosmetic.dto.product;

import com.mycosmetic.entity.Category;
import com.mycosmetic.entity.Product;

/**
 * 제품 요약 (바코드 조회, 이름 검색, 화장대 항목, 상세의 product).
 *
 * @param representativeListId 대표 성분 버전(설계 3.5-4). 버전이 없으면 null
 */
public record ProductSummaryResponse(Long id, String barcode, String brand, String name, String volume,
		Category category, Long representativeListId) {

	public static ProductSummaryResponse of(Product product, Long representativeListId) {
		return new ProductSummaryResponse(product.getId(), product.getBarcode(), product.getBrand(),
				product.getName(), product.getVolume(), product.getCategory(), representativeListId);
	}
}
