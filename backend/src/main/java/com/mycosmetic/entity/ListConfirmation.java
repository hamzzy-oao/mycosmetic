package com.mycosmetic.entity;

import java.time.Instant;

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

/** 사용자 한 명이 성분 버전을 확인한 기록. 탈퇴하면 user가 NULL(익명)이 된다. */
@Entity
@Table(name = "list_confirmations")
public class ListConfirmation {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "list_id", nullable = false)
	private IngredientList list;

	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "user_id")
	private User user;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected ListConfirmation() {
	}

	private ListConfirmation(IngredientList list, User user) {
		this.list = list;
		this.user = user;
	}

	public static ListConfirmation create(IngredientList list, User user) {
		return new ListConfirmation(list, user);
	}

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}

	public Long getId() {
		return id;
	}

	public IngredientList getList() {
		return list;
	}

	public User getUser() {
		return user;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
