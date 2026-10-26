package com.mycosmetic.service.product.seed;

import java.util.List;
import java.util.Map;

/**
 * 시드 넣기 결과. seed-products 스킬의 보고서 JSON 형식 그대로 쓴다.
 *
 * @param created         새로 만든 성분 버전 수(dry-run이면 만들 예정인 수)
 * @param createdProducts 그중 제품까지 새로 만든 수
 * @param alreadyExists   같은 제품에 같은 성분 버전이 이미 있어 건너뛴 수
 * @param items           넣은(넣을) 줄의 매칭 종류별 성분 줄 수(EXACT/ALIAS/FUZZY/UNMATCHED)
 * @param unmatched       FUZZY·UNMATCHED 원문과 나온 횟수(많은 순, 최대 {@link ProductSeedService#UNMATCHED_TOP}개)
 * @param byKind          넣은(넣을)·이미 있는 줄의 종류별 수. kind가 비면 "미분류"
 * @param byCategory      같은 줄의 카테고리별 수
 */
public record SeedReport(boolean dryRun, int rows, int created, int createdProducts, int alreadyExists,
		List<LineReason> skipped, List<LineReason> failed, Map<String, Integer> items, List<NameCount> unmatched,
		Map<String, Integer> byKind, Map<String, Integer> byCategory) {

	public record LineReason(int line, String reason) {
	}

	public record NameCount(String rawText, int count) {
	}
}
