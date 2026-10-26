package com.mycosmetic.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

/** 성분의 다른 이름. 표준명도 한 줄로 들어가서 매칭은 이 테이블만 본다. */
@Entity
@Table(name = "ingredient_aliases")
public class IngredientAlias {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "ingredient_id", nullable = false)
	private Ingredient ingredient;

	@Column(nullable = false, length = 300)
	private String alias;

	@Column(name = "normalized_alias", nullable = false, length = 300)
	private String normalizedAlias;

	protected IngredientAlias() {
	}

	private IngredientAlias(Ingredient ingredient, String alias, String normalizedAlias) {
		this.ingredient = ingredient;
		this.alias = alias;
		this.normalizedAlias = normalizedAlias;
	}

	public static IngredientAlias create(Ingredient ingredient, String alias, String normalizedAlias) {
		return new IngredientAlias(ingredient, alias, normalizedAlias);
	}

	public Long getId() {
		return id;
	}

	public Ingredient getIngredient() {
		return ingredient;
	}

	public String getAlias() {
		return alias;
	}

	public String getNormalizedAlias() {
		return normalizedAlias;
	}
}
