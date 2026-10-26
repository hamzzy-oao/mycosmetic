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

/** 모든 사용자가 공유하는 제품 정보 */
@Entity
@Table(name = "products")
public class Product {
	@Column(nullable = false)
	private boolean archived;
	public boolean isArchived() { return archived; }
	public void archive() { archived = true; }
	public void edit(String brand, String name, String volume, Category category) {
		this.brand = brand; this.name = name; this.volume = volume; this.category = category;
	}

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@Column(length = 50)
	private String barcode;

	@Column(nullable = false, length = 100)
	private String brand;

	@Column(nullable = false, length = 200)
	private String name;

	/** 모르면 null(입력은 선택) */
	@Column(length = 50)
	private String volume;

	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 20)
	private Category category;

	/** 브랜드·제품명을 어디서 가져왔는지(V17) */
	@Enumerated(EnumType.STRING)
	@JdbcTypeCode(SqlTypes.VARCHAR)
	@Column(nullable = false, length = 10)
	private ProductSource source;

	/** 관리자가 확인했거나 믿을 수 있는 출처(SEED·LINK). 사용자가 직접 입력한 제품은 false로 시작한다 */
	@Column(nullable = false)
	private boolean verified;

	/** 탈퇴하면 DB가 NULL로 바꾼다(익명) */
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "created_by")
	private User createdBy;

	@Column(name = "created_at", nullable = false, updatable = false)
	private Instant createdAt;

	protected Product() {
	}

	private Product(String barcode, String brand, String name, String volume, Category category, User createdBy) {
		this.barcode = barcode;
		this.brand = brand;
		this.name = name;
		this.volume = volume;
		this.category = category;
		this.createdBy = createdBy;
		// 등록한 사람이 없으면 시드(실행기·관리자 데이터), 있으면 사용자 입력. 링크 이름 그대로면 fromLink()로 바꾼다
		this.source = createdBy == null ? ProductSource.SEED : ProductSource.USER;
		this.verified = createdBy == null;
	}

	/** 올리브영 링크에서 가져온 이름을 그대로 저장한 제품. 쇼핑몰 표기라 믿을 만해 검수된 것으로 본다 */
	public void fromLink() {
		this.source = ProductSource.LINK;
		this.verified = true;
	}

	/** 관리자가 확인함 */
	public void verify() {
		this.verified = true;
	}

	public ProductSource getSource() {
		return source;
	}

	public boolean isVerified() {
		return verified;
	}

	public static Product create(String barcode, String brand, String name, String volume, Category category,
			User createdBy) {
		return new Product(barcode, brand, name, volume, category, createdBy);
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

	public String getBarcode() {
		return barcode;
	}

	public String getBrand() {
		return brand;
	}

	public String getName() {
		return name;
	}

	public String getVolume() {
		return volume;
	}

	public Category getCategory() {
		return category;
	}

	public User getCreatedBy() {
		return createdBy;
	}

	public Instant getCreatedAt() {
		return createdAt;
	}
}
