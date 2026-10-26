package com.mycosmetic.entity;

import java.time.Instant;
import java.time.LocalDate;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** 새 제품 테스트(설계 2026-10-24 B). 화장대 항목 하나에 하나. 기간 동안의 기록은 피부 일기를 센다. 🔒 */
@Entity
@Table(name = "product_trials")
public class ProductTrial {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "shelf_item_id", nullable = false, updatable = false)
	private ShelfItem shelfItem;

	@Column(name = "started_on", nullable = false, updatable = false)
	private LocalDate startedOn;

	@Column(name = "ends_on", nullable = false, updatable = false)
	private LocalDate endsOn;

	/** 평가를 마친 시각. null이면 아직 진행 중이거나 평가 대기 */
	@Column(name = "finished_at")
	private Instant finishedAt;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected ProductTrial() {
	}

	private ProductTrial(Long userId, ShelfItem shelfItem, LocalDate startedOn, LocalDate endsOn) {
		this.userId = userId;
		this.shelfItem = shelfItem;
		this.startedOn = startedOn;
		this.endsOn = endsOn;
	}

	public static ProductTrial start(Long userId, ShelfItem shelfItem, LocalDate startedOn, LocalDate endsOn) {
		return new ProductTrial(userId, shelfItem, startedOn, endsOn);
	}

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}

	public void finish(Instant at) {
		this.finishedAt = at;
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public ShelfItem getShelfItem() {
		return shelfItem;
	}

	public LocalDate getStartedOn() {
		return startedOn;
	}

	public LocalDate getEndsOn() {
		return endsOn;
	}

	public Instant getFinishedAt() {
		return finishedAt;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
