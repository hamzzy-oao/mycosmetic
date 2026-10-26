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

/** 성분 추출 작업. 사진은 저장하지 않고 결과 JSON만 둔다. */
@Entity
@Table(name = "extraction_jobs")
public class ExtractionJob {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private JobStatus status;

	@JdbcTypeCode(SqlTypes.JSON)
	@Column(name = "result_json")
	private String resultJson;

	@Column(name = "error_code", length = 50)
	private String errorCode;

	/** 하루 한도에 포함되는지. 서버 사정으로 실패하면 false로 바꾼다. */
	@Column(name = "counts_toward_limit", nullable = false)
	private boolean countsTowardLimit;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	/** 작업 스레드가 RUNNING으로 바꾼 시각. 정리 스케줄러가 멈춘 RUNNING job을 찾을 때 쓴다 */
	@Column(name = "started_at")
	private Instant startedAt;

	@Column(name = "finished_at")
	private Instant finishedAt;

	/** 사용자가 결과를 확인·저장한 시각. 한 job은 한 번만 저장할 수 있다 */
	@Column(name = "confirmed_at")
	private Instant confirmedAt;

	protected ExtractionJob() {
	}

	private ExtractionJob(User user, Instant createdAt) {
		this.user = user;
		this.status = JobStatus.QUEUED;
		this.countsTowardLimit = true;
		this.createdAt = createdAt;
	}

	/** 접수된 작업. QUEUED이고 한도에 포함된다. 생성 시각은 저장할 때 정한다. */
	public static ExtractionJob queue(User user) {
		return new ExtractionJob(user, null);
	}

	/** 접수된 작업. 하루 한도 계산과 같은 Clock의 시각을 생성 시각으로 쓴다. */
	public static ExtractionJob queue(User user, Instant createdAt) {
		return new ExtractionJob(user, createdAt);
	}

	@PrePersist
	void onCreate() {
		if (createdAt == null) {
			createdAt = Instant.now();
		}
	}

	public void start(Instant now) {
		this.status = JobStatus.RUNNING;
		this.startedAt = now;
	}

	public void complete(String resultJson, Instant now) {
		this.status = JobStatus.DONE;
		this.resultJson = resultJson;
		this.finishedAt = now;
	}

	/** 실패로 끝낸다. countsTowardLimit가 false면 하루 한도에서 빠진다. */
	public void fail(String errorCode, boolean countsTowardLimit, Instant now) {
		this.status = JobStatus.FAILED;
		this.errorCode = errorCode;
		this.countsTowardLimit = countsTowardLimit;
		this.finishedAt = now;
	}

	/** 하루 한도에 포함시킨다. 한도에서 뺐던 FAILED job으로 직접 붙여넣어 저장할 때 1회로 센다 */
	public void countTowardLimit() {
		this.countsTowardLimit = true;
	}

	/** 확인·저장을 마쳤다. 화면 계약(DONE인데 result null = 이미 확인됨)에 맞춰 결과 JSON도 지운다 */
	public void markConfirmed(Instant now) {
		this.resultJson = null;
		this.confirmedAt = now;
	}

	public boolean isConfirmed() {
		return confirmedAt != null;
	}

	public boolean isFinished() {
		return status == JobStatus.DONE || status == JobStatus.FAILED;
	}

	public Long getId() {
		return id;
	}

	public User getUser() {
		return user;
	}

	public JobStatus getStatus() {
		return status;
	}

	public String getResultJson() {
		return resultJson;
	}

	public String getErrorCode() {
		return errorCode;
	}

	public boolean isCountsTowardLimit() {
		return countsTowardLimit;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getStartedAt() {
		return startedAt;
	}

	public Instant getFinishedAt() {
		return finishedAt;
	}

	public Instant getConfirmedAt() {
		return confirmedAt;
	}
}
