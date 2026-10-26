package com.mycosmetic.entity;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** 성분 버전 안의 성분 한 줄. 매칭되지 않으면 ingredient가 없다. */
@Entity
@Table(name = "ingredient_list_items")
public class IngredientListItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "list_id", nullable = false)
	private IngredientList list;

	@Column(nullable = false)
	private int position;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "ingredient_id")
	private Ingredient ingredient;

	@Column(name = "raw_text", nullable = false, length = 500)
	private String rawText;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "match_type", nullable = false, length = 20)
	private MatchType matchType;

	protected IngredientListItem() {
	}

	private IngredientListItem(IngredientList list, int position, Ingredient ingredient, String rawText,
			MatchType matchType) {
		this.list = list;
		this.position = position;
		this.ingredient = ingredient;
		this.rawText = rawText;
		this.matchType = matchType;
	}

	public static IngredientListItem create(IngredientList list, int position, Ingredient ingredient,
			String rawText, MatchType matchType) {
		return new IngredientListItem(list, position, ingredient, rawText, matchType);
	}

	/**
	 * 사전이 늘어난 뒤 "사전에 없음" 줄을 성분에 잇는다(설계 4.5 다시 매칭). 원문(rawText)과 순서는 바꾸지 않는다.
	 * 이미 성분이 있는 줄은 바꾸지 않는다.
	 */
	public void linkIngredient(Ingredient ingredient, MatchType matchType) {
		if (this.matchType != MatchType.UNMATCHED || ingredient == null) {
			throw new IllegalStateException("사전에 없는 줄만 성분에 이을 수 있다");
		}
		this.ingredient = ingredient;
		this.matchType = matchType;
	}

	public Long getId() {
		return id;
	}

	public IngredientList getList() {
		return list;
	}

	public int getPosition() {
		return position;
	}

	public Ingredient getIngredient() {
		return ingredient;
	}

	public String getRawText() {
		return rawText;
	}

	public MatchType getMatchType() {
		return matchType;
	}
}
