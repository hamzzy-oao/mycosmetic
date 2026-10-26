package com.mycosmetic.service.product;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.stream.Collectors;

import com.mycosmetic.dto.product.FinalItem;
import com.mycosmetic.service.ingredient.IngredientNameNormalizer;

/**
 * 성분 버전 fingerprint (설계 3.5-1).
 * 순서대로 토큰(매칭된 성분은 ingredientId 숫자, 아니면 {@code "raw:"} + 정규화한 rawText)을 {@code "\n"}으로 이어 붙인
 * 문자열의 SHA-256 16진수 소문자 64자.
 */
public final class ListFingerprint {

	private static final String RAW_PREFIX = "raw:";

	private ListFingerprint() {
	}

	public static String of(List<FinalItem> items) {
		String joined = items.stream()
				.map(ListFingerprint::token)
				.collect(Collectors.joining("\n"));
		return HexFormat.of().formatHex(sha256(joined));
	}

	/** 버전 비교(diff)에서도 같은 키를 쓴다 */
	public static String token(FinalItem item) {
		if (item.ingredientId() != null) {
			return item.ingredientId().toString();
		}
		return RAW_PREFIX + IngredientNameNormalizer.normalize(item.rawText()).value();
	}

	private static byte[] sha256(String s) {
		try {
			return MessageDigest.getInstance("SHA-256").digest(s.getBytes(StandardCharsets.UTF_8));
		} catch (NoSuchAlgorithmException e) {
			// 모든 JDK가 SHA-256을 제공해야 한다
			throw new IllegalStateException("SHA-256을 쓸 수 없습니다", e);
		}
	}
}
