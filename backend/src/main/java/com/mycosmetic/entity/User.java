package com.mycosmetic.entity;

import java.time.Duration;
import java.time.Instant;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.Table;

/** 회원. 이메일 가입자는 email·password_hash가 있고, 카카오 가입자는 없을 수 있다. */
@Entity
@Table(name = "users")
public class User {

	@jakarta.persistence.Enumerated(jakarta.persistence.EnumType.STRING)
	@org.hibernate.annotations.JdbcTypeCode(org.hibernate.type.SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private UserRole role = UserRole.USER;

	public UserRole getRole() { return role; }

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(length = 255)
	private String email;

	@Column(name = "password_hash", length = 100)
	private String passwordHash;

	@Column(nullable = false, length = 50)
	private String nickname;

	@Column(name = "failed_login_count", nullable = false)
	private int failedLoginCount;

	@Column(name = "locked_until")
	private Instant lockedUntil;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected User() {
	}

	private User(String email, String passwordHash, String nickname) {
		this.email = email;
		this.passwordHash = passwordHash;
		this.nickname = nickname;
	}

	/** 이메일 가입 회원 */
	public static User ofEmail(String email, String passwordHash, String nickname) {
		return new User(email, passwordHash, nickname);
	}

	/** 소셜 가입 회원. 제공자가 이메일을 주지 않으면 null */
	public static User ofSocial(String email, String nickname) {
		return new User(email, null, nickname);
	}

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}

	public boolean isLockedAt(Instant now) {
		return lockedUntil != null && lockedUntil.isAfter(now);
	}

	/**
	 * 로그인 실패를 센다. maxFailures번째 실패면 lockDuration 동안 잠근다(횟수는 그대로 남긴다).
	 * 이미 지난 잠금이 있으면 처음부터 다시 센다. 잠겨 있는 동안의 시도는 호출하는 쪽이 세지 않는다.
	 */
	public void recordLoginFailure(int maxFailures, Duration lockDuration, Instant now) {
		if (lockedUntil != null && !isLockedAt(now)) {
			lockedUntil = null;
			failedLoginCount = 0;
		}
		failedLoginCount++;
		if (failedLoginCount >= maxFailures) {
			lockedUntil = now.plus(lockDuration);
		}
	}

	public void recordLoginSuccess() {
		failedLoginCount = 0;
		lockedUntil = null;
	}

	public void changeNickname(String nickname) {
		this.nickname = nickname;
	}

	public Long getId() {
		return id;
	}

	public String getEmail() {
		return email;
	}

	public String getPasswordHash() {
		return passwordHash;
	}

	public String getNickname() {
		return nickname;
	}

	public int getFailedLoginCount() {
		return failedLoginCount;
	}

	public Instant getLockedUntil() {
		return lockedUntil;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
