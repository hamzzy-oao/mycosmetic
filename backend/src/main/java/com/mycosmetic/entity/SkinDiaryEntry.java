package com.mycosmetic.entity;

import java.time.Instant;
import java.time.LocalDate;
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
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** 피부 일기 하루 한 줄(설계 2026-10-24 A). 🔒 */
@Entity
@Table(name = "skin_diary_entries")
public class SkinDiaryEntry {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(name = "user_id", nullable = false, updatable = false)
	private Long userId;

	/** Asia/Seoul 날짜 */
	@Column(name = "entry_date", nullable = false, updatable = false)
	private LocalDate entryDate;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private DiaryMood mood;

	@Convert(converter = DiaryTagsConverter.class)
	@Column(nullable = false, columnDefinition = "json")
	private Set<DiaryTag> tags;

	@Column(length = 200)
	private String memo;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected SkinDiaryEntry() {
	}

	private SkinDiaryEntry(Long userId, LocalDate entryDate) {
		this.userId = userId;
		this.entryDate = entryDate;
	}

	public static SkinDiaryEntry create(Long userId, LocalDate entryDate, DiaryMood mood, Set<DiaryTag> tags,
			String memo) {
		SkinDiaryEntry entry = new SkinDiaryEntry(userId, entryDate);
		entry.change(mood, tags, memo);
		return entry;
	}

	@PrePersist
	void onCreate() {
		Instant now = Instant.now();
		if (createdAt == null) {
			createdAt = now;
		}
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = Instant.now();
	}

	/** 기분·태그·메모를 통째로 바꾼다 */
	public void change(DiaryMood mood, Set<DiaryTag> tags, String memo) {
		this.mood = mood;
		this.tags = tags == null || tags.isEmpty() ? EnumSet.noneOf(DiaryTag.class) : EnumSet.copyOf(tags);
		this.memo = memo;
	}

	public Long getId() {
		return id;
	}

	public Long getUserId() {
		return userId;
	}

	public LocalDate getEntryDate() {
		return entryDate;
	}

	public DiaryMood getMood() {
		return mood;
	}

	public Set<DiaryTag> getTags() {
		return Collections.unmodifiableSet(tags);
	}

	public String getMemo() {
		return memo;
	}
}
