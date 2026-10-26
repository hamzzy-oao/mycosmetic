package com.mycosmetic.service.product;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import com.mycosmetic.dto.product.ConfirmRequest;
import com.mycosmetic.dto.product.ConfirmRequest.NewProduct;
import com.mycosmetic.dto.product.ConfirmResponse;
import com.mycosmetic.dto.product.FinalItem;
import com.mycosmetic.entity.Category;
import com.mycosmetic.entity.ListStatus;
import com.mycosmetic.service.product.review.ProductNameCheckService;

/** 새 제품 이름이 올리브영 링크 표기 그대로면 출처를 LINK로 바꾼다(2026-10-26) */
class ProductConfirmLinkSourceTest {

	static final String URL = "https://www.oliveyoung.co.kr/store/goods/getGoodsDetail.do?goodsNo=A000000123456";

	final ProductConfirmTxService txService = mock(ProductConfirmTxService.class);
	final ProductNameCheckService nameCheck = mock(ProductNameCheckService.class);
	final ProductLinkService linkService = mock(ProductLinkService.class);
	final ProductConfirmService service = new ProductConfirmService(txService, nameCheck, linkService);

	@BeforeEach
	void setUp() {
		when(nameCheck.sameNameProduct(any(), any())).thenReturn(Optional.empty());
		when(txService.confirmOnce(anyLong(), any())).thenReturn(new ConfirmResponse(10L, 20L, ListStatus.PENDING, 30L));
	}

	private static ConfirmRequest request(String name, String sourceUrl) {
		return new ConfirmRequest(1L, null,
				new NewProduct(null, "설화수", name, null, Category.SKINCARE, sourceUrl, false),
				List.of(new FinalItem(null, "정제수")));
	}

	@Test
	@DisplayName("링크 이름 그대로면(쇼핑몰 표기 문구만 다름) LINK로 표시한다")
	void sameAsLink() {
		when(linkService.officialName(URL)).thenReturn("[단독기획] 설화수 윤조에센스 60ml");

		service.confirm(7L, request("윤조에센스", URL));

		verify(nameCheck).markFromLink(10L, 7L);
	}

	@Test
	@DisplayName("이름을 바꿨거나, 링크를 못 가져왔거나, 링크가 없으면 사용자 입력 그대로 둔다")
	void notFromLink() {
		when(linkService.officialName(URL)).thenReturn("설화수 윤조에센스 60ml").thenReturn(null);

		service.confirm(7L, request("자음생크림", URL));
		service.confirm(7L, request("윤조에센스", URL));
		service.confirm(7L, request("윤조에센스", null));

		verify(nameCheck, never()).markFromLink(anyLong(), anyLong());
	}
}
