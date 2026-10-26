package com.mycosmetic.entity;

import java.time.DayOfWeek;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Set;

import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.PrePersist;
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** 내 루틴(아침/저녁). days가 비면 매일 한다 */
@Entity
@Table(name = "routines")
public class Routine {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@Column(nullable = false, length = 50)
	private String name;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private RoutineSlot slot;

	@Convert(converter = DaysOfWeekConverter.class)
	@Column(nullable = false, columnDefinition = "json")
	private Set<DayOfWeek> days;

	/** 화장대 진단의 "루틴으로 저장"으로 만든 루틴. 다시 저장하면 새 초안으로 바뀐다 */
	@Column(name = "from_diagnosis", nullable = false)
	private boolean fromDiagnosis;

	@OneToMany(mappedBy = "routine", cascade = CascadeType.ALL, orphanRemoval = true)
	@OrderBy("position ASC")
	private List<RoutineStep> steps = new ArrayList<>();

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected Routine() {
	}

	private Routine(User user, String name, RoutineSlot slot, Set<DayOfWeek> days) {
		this.user = user;
		this.name = name;
		this.slot = slot;
		this.days = copyOf(days);
	}

	public static Routine create(User user, String name, RoutineSlot slot, Set<DayOfWeek> days) {
		return new Routine(user, name, slot, days);
	}

	/** 화장대 진단 초안으로 만든 매일 루틴 */
	public static Routine fromDiagnosis(User user, String name, RoutineSlot slot) {
		Routine routine = new Routine(user, name, slot, Set.of());
		routine.fromDiagnosis = true;
		return routine;
	}

	@PrePersist
	void onCreate() {
		Instant now = now();
		if (createdAt == null) {
			createdAt = now;
		}
		updatedAt = now;
	}

	@PreUpdate
	void onUpdate() {
		updatedAt = now();
	}

	public void change(String name, RoutineSlot slot, Set<DayOfWeek> days) {
		this.name = name;
		this.slot = slot;
		this.days = copyOf(days);
		this.updatedAt = now();
	}

	/** 단계를 통째로 바꾼다. 순서는 목록 순서(1부터) */
	public void replaceSteps(List<ShelfItem> items, List<StepType> types) {
		steps.clear();
		for (int i = 0; i < items.size(); i++) {
			steps.add(new RoutineStep(this, items.get(i), types.get(i), i + 1));
		}
		this.updatedAt = now();
	}

	/** 오늘 요일에 하는 루틴인가(days가 비면 매일) */
	public boolean runsOn(DayOfWeek day) {
		return days.isEmpty() || days.contains(day);
	}

	/** DB(DATETIME(6))와 같은 마이크로초 단위. 저장 직후 응답과 다시 읽은 값이 같도록 */
	private static Instant now() {
		return Instant.now().truncatedTo(ChronoUnit.MICROS);
	}

	private static Set<DayOfWeek> copyOf(Set<DayOfWeek> days) {
		return days == null || days.isEmpty() ? EnumSet.noneOf(DayOfWeek.class) : EnumSet.copyOf(days);
	}

	public Long getId() {
		return id;
	}

	public User getUser() {
		return user;
	}

	public String getName() {
		return name;
	}

	public RoutineSlot getSlot() {
		return slot;
	}

	public Set<DayOfWeek> getDays() {
		return Collections.unmodifiableSet(days);
	}

	public boolean isFromDiagnosis() {
		return fromDiagnosis;
	}

	public List<RoutineStep> getSteps() {
		return Collections.unmodifiableList(steps);
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
