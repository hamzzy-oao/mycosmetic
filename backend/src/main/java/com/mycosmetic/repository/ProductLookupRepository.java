package com.mycosmetic.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

import com.mycosmetic.entity.Product;

/** 제품명 점검(2026-10-26): 같은 브랜드의 제품과 브랜드 목록을 읽는다. 보관한 제품은 빼고 본다 */
public interface ProductLookupRepository extends Repository<Product, Long> {

	List<Product> findByArchivedFalse();

	/** [브랜드 표기, 제품 수] */
	@Query("select p.brand, count(p) from Product p where p.archived = false group by p.brand")
	List<Object[]> countByBrand();

	Optional<Product> findById(Long id);
}
