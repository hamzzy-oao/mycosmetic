package com.mycosmetic.service.ingredient;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.mycosmetic.dto.ingredient.IngredientSection;

/** 전성분 글(붙여넣기, 상세페이지 상품정보)을 구역별로 나누고 사전과 맞춘다 */
@Service
public class IngredientTextService {

	private final IngredientMatcher matcher;

	public IngredientTextService(IngredientMatcher matcher) {
		this.matcher = matcher;
	}

	@Transactional(readOnly = true)
	public List<IngredientSection> matchSections(String text) {
		return IngredientTextSections.parse(text).stream()
				.map(section -> new IngredientSection(section.label(), matcher.match(section.names())))
				.toList();
	}
}
