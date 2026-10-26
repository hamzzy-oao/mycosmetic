package com.mycosmetic.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** 식약처 성분 사전의 성분 하나 */
@Entity
@Table(name = "ingredients")
public class Ingredient {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "standard_name", nullable = false, length = 200)
	private String standardName;

	@Column(name = "english_name", length = 300)
	private String englishName;

	@Column(name = "cas_no", length = 100)
	private String casNo;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Ingredient() {
	}

	private Ingredient(String standardName, String englishName, String casNo) {
		this.standardName = standardName;
		this.englishName = englishName;
		this.casNo = casNo;
	}

	public static Ingredient create(String standardName, String englishName, String casNo) {
		return new Ingredient(standardName, englishName, casNo);
	}

	@PrePersist
	@PreUpdate
	void touch() {
		updatedAt = Instant.now();
	}

	/** 사전을 다시 가져올 때 영문명·CAS 번호를 갱신한다 */
	public void updateDetails(String englishName, String casNo) {
		this.englishName = englishName;
		this.casNo = casNo;
	}

	public Long getId() {
		return id;
	}

	public String getStandardName() {
		return standardName;
	}

	public String getEnglishName() {
		return englishName;
	}

	public String getCasNo() {
		return casNo;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
