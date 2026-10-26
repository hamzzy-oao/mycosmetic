package com.mycosmetic.entity;

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

/** 루틴 단계 하나. 내 화장대 항목을 가리킨다 */
@Entity
@Table(name = "routine_steps")
public class RoutineStep {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "routine_id", nullable = false)
	private Routine routine;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "shelf_item_id", nullable = false)
	private ShelfItem shelfItem;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "step_type", nullable = false, length = 20)
	private StepType stepType;

	@Column(nullable = false)
	private int position;

	protected RoutineStep() {
	}

	RoutineStep(Routine routine, ShelfItem shelfItem, StepType stepType, int position) {
		this.routine = routine;
		this.shelfItem = shelfItem;
		this.stepType = stepType;
		this.position = position;
	}

	public Long getId() {
		return id;
	}

	public Routine getRoutine() {
		return routine;
	}

	public ShelfItem getShelfItem() {
		return shelfItem;
	}

	public StepType getStepType() {
		return stepType;
	}

	public int getPosition() {
		return position;
	}
}
