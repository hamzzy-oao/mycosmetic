package com.mycosmetic.dto.product;

import java.util.List;

import com.mycosmetic.entity.Category;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * POST /api/products/confirm 본문 (설계 4.1 9단계). productId와 newProduct 중 정확히 하나를 보낸다.
 *
 * @param jobId      확인하는 추출 job. 본인 것이고 DONE 또는 FAILED(직접 붙여넣기)여야 한다
 * @param productId  기존 제품 ID
 * @param newProduct 새 제품 정보. barcode가 이미 있으면 그 제품을 쓴다
 * @param items      화면에 보이는 순서대로의 최종 성분 목록
 * @param paoMonths  개봉 후 사용기간(개월, 1~60). 새로 담는 화장대 항목에만 넣는다. 모르면 null
 */
public record ConfirmRequest(
		@NotNull Long jobId,
		Long productId,
		@Valid NewProduct newProduct,
		@NotEmpty @Size(max = 200) List<@NotNull @Valid FinalItem> items,
		@Min(1) @Max(60) Integer paoMonths) {

	public ConfirmRequest(Long jobId, Long productId, NewProduct newProduct, List<FinalItem> items) {
		this(jobId, productId, newProduct, items, null);
	}

	/**
	 * @param barcode          없으면 null
	 * @param volume           모르면 null
	 * @param sourceUrl        올리브영 링크에서 가져왔으면 그 주소. 이름이 링크 표기 그대로면 출처가 LINK가 된다(2026-10-26)
	 * @param differentProduct 같은 이름의 제품이 있어도 다른 제품이라고 사용자가 확인함. 없거나 false면 같은 이름 제품이 있을 때
	 *                         PRODUCT_SAME_NAME_EXISTS(예전 화면이 보내지 않아도 400이 나지 않게 Boolean)
	 */
	public record NewProduct(
			@Size(max = 50) String barcode,
			@NotBlank @Size(max = 100) String brand,
			@NotBlank @Size(max = 200) String name,
			@Size(max = 50) String volume,
			@NotNull Category category,
			@Size(max = 500) String sourceUrl,
			Boolean differentProduct) {

		/** 링크·중복 확인 없이 만드는 새 제품 */
		public NewProduct(String barcode, String brand, String name, String volume, Category category) {
			this(barcode, brand, name, volume, category, null, false);
		}

		public boolean confirmedDifferent() {
			return Boolean.TRUE.equals(differentProduct);
		}
	}
}
