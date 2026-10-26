package com.mycosmetic.service.product.seed;

/**
 * 시드 CSV 한 줄 (seed-products 스킬의 templates/products.csv 형식). 값은 앞뒤 공백을 뺀 그대로이고, 빈 칸은 빈 문자열이다.
 *
 * @param line        CSV에서 이 줄이 시작하는 줄 번호(머리글이 1)
 * @param kind        보고서 집계용 종류(토너, 세럼 등). DB에는 넣지 않는다
 * @param ingredients 전성분 글 그대로
 */
public record SeedRow(int line, String brand, String name, String volume, String category, String kind,
		String ingredients, String sourceType, String sourceUrl) {
}
