package com.mycosmetic.entity;

import java.time.Instant;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** 퍼스널컬러 분석 한 건(퍼스널 탭 설계 2절). 사진은 저장하지 않는다. 만든 뒤 바꾸지 않는다 */
@Entity
@Table(name = "personal_color_analyses")
public class PersonalColorAnalysis {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, updatable = false, length = 10)
	private ColorMethod method;

	@Column(name = "lab_l", updatable = false)
	private Double labL;

	@Column(name = "lab_a", updatable = false)
	private Double labA;

	@Column(name = "lab_b", updatable = false)
	private Double labB;

	@Column(updatable = false)
	private Double ita;

	@Column(updatable = false)
	private Double hue;

	@Column(updatable = false)
	private Double confidence;

	/** {passed, issues, score} JSON */
	@Column(updatable = false, columnDefinition = "json")
	private String quality;

	/** 카메라 판정 후보 JSON 배열 */
	@Column(updatable = false, columnDefinition = "json")
	private String candidates;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, updatable = false, length = 20)
	private PersonalColor chosen;

	/** 카메라 1순위 후보와 같은가. MANUAL이면 null */
	@Column(updatable = false)
	private Boolean agreed;

	@Column(name = "rule_version", nullable = false, updatable = false, length = 20)
	private String ruleVersion;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected PersonalColorAnalysis() {
	}

	public static PersonalColorAnalysis manual(Long userId, PersonalColor chosen, String ruleVersion) {
		PersonalColorAnalysis a = new PersonalColorAnalysis();
		a.userId = userId;
		a.method = ColorMethod.MANUAL;
		a.chosen = chosen;
		a.ruleVersion = ruleVersion;
		return a;
	}

	/** @param lab {l, a, b} */
	public static PersonalColorAnalysis camera(Long userId, double[] lab, double ita, double hue, double confidence,
			String qualityJson, String candidatesJson, PersonalColor chosen, boolean agreed, String ruleVersion) {
		PersonalColorAnalysis a = manual(userId, chosen, ruleVersion);
		a.method = ColorMethod.CAMERA;
		a.labL = lab[0];
		a.labA = lab[1];
		a.labB = lab[2];
		a.ita = ita;
		a.hue = hue;
		a.confidence = confidence;
		a.quality = qualityJson;
		a.candidates = candidatesJson;
		a.agreed = agreed;
		return a;
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

	public ColorMethod getMethod() {
		return method;
	}

	public Double getLabL() {
		return labL;
	}

	public Double getLabA() {
		return labA;
	}

	public Double getLabB() {
		return labB;
	}

	public Double getIta() {
		return ita;
	}

	public Double getHue() {
		return hue;
	}

	public Double getConfidence() {
		return confidence;
	}

	public String getQuality() {
		return quality;
	}

	public String getCandidates() {
		return candidates;
	}

	public PersonalColor getChosen() {
		return chosen;
	}

	public Boolean getAgreed() {
		return agreed;
	}

	public String getRuleVersion() {
		return ruleVersion;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
