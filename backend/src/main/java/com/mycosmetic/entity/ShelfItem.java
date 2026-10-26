package com.mycosmetic.entity;

import java.time.Instant;
import java.time.LocalDate;

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
import jakarta.persistence.PreUpdate;
import jakarta.persistence.Table;

/** 내 화장대 항목. 내가 확인한 성분 버전에 고정된다(설계 3.5-5). */
@Entity
@Table(name = "shelf_items")
public class ShelfItem {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "user_id", nullable = false)
	private User user;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "product_id", nullable = false)
	private Product product;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "ingredient_list_id", nullable = false)
	private IngredientList ingredientList;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private Verdict verdict;

	@Column(length = 1000)
	private String memo;

	@Column(name = "started_on")
	private LocalDate startedOn;

	@Column(name = "ended_on")
	private LocalDate endedOn;

	/** 개봉일 */
	@Column(name = "opened_on")
	private LocalDate openedOn;

	/** 개봉 후 사용기한(개월, 1~60) */
	@Column(name = "pao_months")
	private Integer paoMonths;

	/** 내 호수 이름(예: 21호, 로즈 베이지). 같은 제품도 사람마다 다르다 */
	@Column(name = "shade_name", length = 50)
	private String shadeName;

	/** 내 호수 색 #RRGGBB(대문자) */
	@Column(name = "shade_hex", length = 7, columnDefinition = "char(7)")
	private String shadeHex;

	/** 스킨케어 단계(퍼스널 탭 설계 3절). null이면 제품 이름으로 추정한다 */
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "step_type", length = 20)
	private StepType stepType;

	/** 색조 종류. null이면 제품 이름으로 추정한다 */
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(name = "makeup_type", length = 10)
	private MakeupType makeupType;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	@Column(name = "updated_at", nullable = false)
	private Instant updatedAt;

	protected ShelfItem() {
	}

	private ShelfItem(User user, Product product, IngredientList ingredientList, Verdict verdict, String memo,
			LocalDate startedOn, LocalDate endedOn) {
		this.user = user;
		this.product = product;
		this.ingredientList = ingredientList;
		this.verdict = verdict;
		this.memo = memo;
		this.startedOn = startedOn;
		this.endedOn = endedOn;
	}

	public static ShelfItem create(User user, Product product, IngredientList ingredientList, Verdict verdict,
			String memo, LocalDate startedOn, LocalDate endedOn) {
		return new ShelfItem(user, product, ingredientList, verdict, memo, startedOn, endedOn);
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

	/** 평가·메모·사용 기간을 바꾼다. 고정된 성분 버전은 바꾸지 않는다. */
	public void update(Verdict verdict, String memo, LocalDate startedOn, LocalDate endedOn) {
		this.verdict = verdict;
		this.memo = memo;
		this.startedOn = startedOn;
		this.endedOn = endedOn;
	}

	/** 개봉일·개봉 후 사용기한(개월)을 바꾼다 */
	public void changeOpening(LocalDate openedOn, Integer paoMonths) {
		this.openedOn = openedOn;
		this.paoMonths = paoMonths;
	}

	/** 호수 이름·색을 바꾼다. 색 형식은 요청 검증과 service에서 확인한다 */
	public void changeShade(String shadeName, String shadeHex) {
		this.shadeName = shadeName;
		this.shadeHex = shadeHex;
	}

	/** null이면 지운다 */
	public void changeTypes(StepType stepType, MakeupType makeupType) {
		this.stepType = stepType;
		this.makeupType = makeupType;
	}

	public StepType getStepType() {
		return stepType;
	}

	public MakeupType getMakeupType() {
		return makeupType;
	}

	public Long getId() {
		return id;
	}

	public User getUser() {
		return user;
	}

	public Product getProduct() {
		return product;
	}

	public IngredientList getIngredientList() {
		return ingredientList;
	}

	public Verdict getVerdict() {
		return verdict;
	}

	public String getMemo() {
		return memo;
	}

	public LocalDate getStartedOn() {
		return startedOn;
	}

	public LocalDate getEndedOn() {
		return endedOn;
	}

	public LocalDate getOpenedOn() {
		return openedOn;
	}

	public Integer getPaoMonths() {
		return paoMonths;
	}

	public String getShadeName() {
		return shadeName;
	}

	public String getShadeHex() {
		return shadeHex;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}

	public Instant getUpdatedAt() {
		return updatedAt;
	}
}
