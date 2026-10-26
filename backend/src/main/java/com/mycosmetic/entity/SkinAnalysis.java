package com.mycosmetic.entity;

import java.time.Instant;
import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** 피부 분석 이력 한 건(퍼스널 탭 설계 1절). 만든 뒤 바꾸지 않는다 */
@Entity
@Table(name = "skin_analyses")
public class SkinAnalysis {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	/** 요청 답변 JSON({selfType, middayFeel, concerns}) */
	@Column(nullable = false, updatable = false, columnDefinition = "json")
	private String answers;

	@Convert(converter = SkinTypesConverter.class)
	@Column(name = "result_types", nullable = false, updatable = false, columnDefinition = "json")
	private Set<SkinType> resultTypes;

	@Convert(converter = SkinConcernsConverter.class)
	@Column(nullable = false, updatable = false, columnDefinition = "json")
	private Set<SkinConcern> concerns;

	@Column(name = "rule_version", nullable = false, updatable = false, length = 20)
	private String ruleVersion;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected SkinAnalysis() {
	}

	private SkinAnalysis(Long userId, String answers, Set<SkinType> resultTypes, Set<SkinConcern> concerns,
			String ruleVersion) {
		this.userId = userId;
		this.answers = answers;
		this.resultTypes = resultTypes.isEmpty() ? EnumSet.noneOf(SkinType.class) : EnumSet.copyOf(resultTypes);
		this.concerns = concerns.isEmpty() ? EnumSet.noneOf(SkinConcern.class) : EnumSet.copyOf(concerns);
		this.ruleVersion = ruleVersion;
	}

	public static SkinAnalysis create(Long userId, String answers, Set<SkinType> resultTypes,
			Set<SkinConcern> concerns, String ruleVersion) {
		return new SkinAnalysis(userId, answers, resultTypes, concerns, ruleVersion);
	}

	@PrePersist
	void onCreate() {
		createdAt = Instant.now();
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public String getAnswers() {
		return answers;
	}

	public Set<SkinType> getResultTypes() {
		return Collections.unmodifiableSet(resultTypes);
	}

	public Set<SkinConcern> getConcerns() {
		return Collections.unmodifiableSet(concerns);
	}

	public String getRuleVersion() {
		return ruleVersion;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
