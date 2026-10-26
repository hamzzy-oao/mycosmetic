package com.mycosmetic.entity;

import java.time.Instant;
import java.time.LocalDate;
import java.time.temporal.ChronoUnit;

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
import jakarta.persistence.Table;

/** 오늘 사용 체크 하나: 이 화장대 항목을 이 날짜·시간대에 썼다. 루틴이 아니라 항목에 묶어 루틴을 바꿔도 남는다 */
@Entity
@Table(name = "routine_checks")
public class RoutineCheck {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "shelf_item_id", nullable = false)
	private ShelfItem shelfItem;

	@Column(name = "used_on", nullable = false)
	private LocalDate usedOn;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private RoutineSlot slot;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected RoutineCheck() {
	}

	private RoutineCheck(User user, ShelfItem shelfItem, LocalDate usedOn, RoutineSlot slot) {
		this.user = user;
		this.shelfItem = shelfItem;
		this.usedOn = usedOn;
		this.slot = slot;
		this.createdAt = Instant.now().truncatedTo(ChronoUnit.MICROS);
	}

	public static RoutineCheck of(User user, ShelfItem shelfItem, LocalDate usedOn, RoutineSlot slot) {
		return new RoutineCheck(user, shelfItem, usedOn, slot);
	}

	public Long getId() {
		return id;
	}

	public ShelfItem getShelfItem() {
		return shelfItem;
	}

	public LocalDate getUsedOn() {
		return usedOn;
	}

	public RoutineSlot getSlot() {
		return slot;
	}
}
