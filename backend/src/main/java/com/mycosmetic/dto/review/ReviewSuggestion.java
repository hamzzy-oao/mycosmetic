package com.mycosmetic.dto.review;

/** 제안대로 고칠 제품 정보. null인 필드는 지금 값 그대로 */
public record ReviewSuggestion(String brand, String name, String volume) {
}
