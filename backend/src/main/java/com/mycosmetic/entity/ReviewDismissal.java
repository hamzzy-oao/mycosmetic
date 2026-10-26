package com.mycosmetic.entity;

import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

/** 관리자 점검함에서 "괜찮음"으로 넘긴 문제. 제품·문제 종류마다 하나. signature가 지금 값과 다르면 다시 뜬다 */
@Entity
@Table(name = "admin_review_dismissals")
public class ReviewDismissal {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "product_id", nullable = false, updatable = false)
	private Long productId;

	@Column(name = "issue_type", nullable = false, updatable = false, length = 30)
	private String issueType;

	@Column(nullable = false, length = 300)
	private String signature;

	@Column(name = "dismissed_by")
	private Long dismissedBy;

	@Column(name = "dismissed_at", nullable = false)
	private Instant dismissedAt;

	protected ReviewDismissal() {
	}

	public static ReviewDismissal of(Long productId, String issueType, String signature, Long dismissedBy,
			Instant at) {
		ReviewDismissal d = new ReviewDismissal();
		d.productId = productId;
		d.issueType = issueType;
		d.dismissed(signature, dismissedBy, at);
		return d;
	}

	/** 같은 문제를 다시 넘기면 값과 시각을 바꾼다 */
	public void dismissed(String signature, Long dismissedBy, Instant at) {
		this.signature = signature;
		this.dismissedBy = dismissedBy;
		this.dismissedAt = at;
	}

	public Long getProductId() {
		return productId;
	}

	public String getIssueType() {
		return issueType;
	}

	public String getSignature() {
		return signature;
	}
}
