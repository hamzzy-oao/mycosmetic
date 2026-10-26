package com.mycosmetic.entity;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** 내 피부 상태. PK가 user_id다(회원당 한 줄). */
@Entity
@Table(name = "skin_profiles")
public class SkinProfile {

	@Id
	@Column(name = "user_id")
	private Long userId;

	@Convert(converter = SkinTypesConverter.class)
	@Column(name = "skin_types", nullable = false, columnDefinition = "json")
	private Set<SkinType> skinTypes;

	/** 피부 고민 0~3개(퍼스널 탭 설계 1절) */
	@Convert(converter = SkinConcernsConverter.class)
	@Column(nullable = false, columnDefinition = "json")
	private Set<SkinConcern> concerns = EnumSet.noneOf(SkinConcern.class);

	/** null = 선택 안 함 */
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "personal_color", length = 20)
	private PersonalColor personalColor;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected SkinProfile() {
	}

	private SkinProfile(Long userId, Set<SkinType> skinTypes) {
		this.userId = userId;
		this.skinTypes = copyOf(skinTypes);
	}

	public static SkinProfile create(Long userId, Set<SkinType> skinTypes) {
		return new SkinProfile(userId, skinTypes);
	}

	@PrePersist
	@PreUpdate
	void touch() {
		updatedAt = Instant.now();
	}

	public void changeSkinTypes(Set<SkinType> skinTypes) {
		this.skinTypes = copyOf(skinTypes);
	}

	public void changeConcerns(Set<SkinConcern> concerns) {
		this.concerns = concerns == null || concerns.isEmpty() ? EnumSet.noneOf(SkinConcern.class)
				: EnumSet.copyOf(concerns);
	}

	/** null이면 지운다(선택 안 함) */
	public void changePersonalColor(PersonalColor personalColor) {
		this.personalColor = personalColor;
	}

	private static Set<SkinType> copyOf(Set<SkinType> skinTypes) {
		return skinTypes == null || skinTypes.isEmpty() ? EnumSet.noneOf(SkinType.class) : EnumSet.copyOf(skinTypes);
	}

	public Long getUserId() {
		return userId;
	}

	public Set<SkinType> getSkinTypes() {
		return Collections.unmodifiableSet(skinTypes);
	}

	public Set<SkinConcern> getConcerns() {
		return Collections.unmodifiableSet(concerns);
	}

	public PersonalColor getPersonalColor() {
		return personalColor;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
