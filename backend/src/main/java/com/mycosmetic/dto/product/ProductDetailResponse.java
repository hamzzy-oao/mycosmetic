package com.mycosmetic.dto.product;

import java.time.Instant;
import java.util.List;

import com.mycosmetic.entity.ListStatus;
import com.mycosmetic.entity.MatchType;

/**
 * 제품 상세: 대표 버전과 나머지 버전 목록 (설계 4.6).
 *
 * @param representative 대표 버전. 버전이 없으면 null
 * @param otherLists     대표 버전을 뺀 나머지, 최신순
 */
public record ProductDetailResponse(ProductSummaryResponse product, ListView representative,
		List<ListBrief> otherLists) {

	public record ListView(Long id, ListStatus status, int confirmCount, List<ItemView> items) {
	}

	/** @param name 매칭된 성분이면 표준명, 아니면 읽은 원문 */
	public record ItemView(int position, Long ingredientId, String name, MatchType matchType) {
	}

	public record ListBrief(Long id, ListStatus status, int confirmCount, Instant createdAt) {
	}
}
