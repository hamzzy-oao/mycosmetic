package com.mycosmetic.dto.advice;

import com.mycosmetic.entity.ShelfItem;
import com.mycosmetic.entity.Verdict;

/** 근거에 나온 내 화장대 항목(화면의 "관련 내 제품" 링크용) */
public record RelatedShelfItem(Long id, Long productId, String brand, String name, Verdict verdict) {

	public static RelatedShelfItem of(ShelfItem item) {
		return new RelatedShelfItem(item.getId(), item.getProduct().getId(), item.getProduct().getBrand(),
				item.getProduct().getName(), item.getVerdict());
	}
}
