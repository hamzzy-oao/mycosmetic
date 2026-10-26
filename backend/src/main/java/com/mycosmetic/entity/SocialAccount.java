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

/** 회원에 연결된 소셜 계정 */
@Entity
@Table(name = "social_accounts")
public class SocialAccount {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private Provider provider;

	@Column(name = "provider_user_id", nullable = false, length = 100)
	private String providerUserId;

	@Column(length = 255)
	private String email;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected SocialAccount() {
	}

	private SocialAccount(User user, Provider provider, String providerUserId, String email) {
		this.user = user;
		this.provider = provider;
		this.providerUserId = providerUserId;
		this.email = email;
	}

	public static SocialAccount link(User user, Provider provider, String providerUserId, String email) {
		return new SocialAccount(user, provider, providerUserId, email);
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

	public User getUser() {
		return user;
	}

	public Provider getProvider() {
		return provider;
	}

	public String getProviderUserId() {
		return providerUserId;
	}

	public String getEmail() {
		return email;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
