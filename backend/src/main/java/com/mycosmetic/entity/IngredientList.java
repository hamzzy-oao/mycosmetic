package com.mycosmetic.entity;

import java.time.Instant;

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
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/**
 * 제품의 성분 버전. 추가만 하고 덮어쓰거나 지우지 않는다(설계 3.5).
 * 예외는 사전을 가져온 뒤의 다시 매칭 하나뿐이다(사전에 없던 줄을 성분에 잇고 fingerprint를 다시 계산, 설계 4.5).
 */
@Entity
@Table(name = "ingredient_lists")
public class IngredientList {

	@Column(name = "admin_approved", nullable = false)
	private boolean adminApproved;

	public boolean isAdminApproved() { return adminApproved; }
	public void approveByAdmin() { adminApproved = true; }
	public void clearAdminApproval() { adminApproved = false; }

	/** 서로 다른 사용자가 이 수 이상 확인하면 VERIFIED */
	public static final int VERIFY_THRESHOLD = 2;

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id", nullable = false)
	private Product product;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private ListStatus status;

	@Column(nullable = false, length = 64)
	private String fingerprint;

	@Column(name = "confirm_count", nullable = false)
	private int confirmCount;

	/** 탈퇴하면 DB가 NULL로 바꾼다(익명) */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by")
	private User createdBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected IngredientList() {
	}

	private IngredientList(Product product, String fingerprint, User createdBy) {
		this.product = product;
		this.fingerprint = fingerprint;
		this.createdBy = createdBy;
		this.status = ListStatus.PENDING;
		this.confirmCount = 0;
	}

	/** 새 버전은 PENDING, 확인 0회로 시작한다. 만든 사람의 확인은 addConfirmation()으로 따로 센다. */
	public static IngredientList create(Product product, String fingerprint, User createdBy) {
		return new IngredientList(product, fingerprint, createdBy);
	}

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}

	/** 서로 다른 사용자의 확인 1회를 더한다. 같은 사용자 중복 확인은 호출 전에 걸러야 한다. */
	public void addConfirmation() {
		confirmCount++;
		if (confirmCount >= VERIFY_THRESHOLD) {
			status = ListStatus.VERIFIED;
		}
	}

	/** 다시 매칭으로 성분 줄이 사전에 이어졌을 때만 쓴다(설계 4.5). 그 외에는 fingerprint를 바꾸지 않는다 */
	public void changeFingerprint(String fingerprint) {
		this.fingerprint = fingerprint;
	}

	public boolean isVerified() {
		return status == ListStatus.VERIFIED;
	}

	public Long getId() {
		return id;
	}

	public Product getProduct() {
		return product;
	}

	public ListStatus getStatus() {
		return status;
	}

	public String getFingerprint() {
		return fingerprint;
	}

	public int getConfirmCount() {
		return confirmCount;
	}

	public User getCreatedBy() {
		return createdBy;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
