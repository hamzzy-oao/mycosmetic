package com.mycosmetic.repository;

import java.util.List;
import java.util.Optional;

import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import com.mycosmetic.entity.Product;

public interface ProductRepository extends JpaRepository<Product, Long> {
	long countByArchivedFalse();

	@org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
	@Query("select p from Product p where p.id = :id")
	Optional<Product> findAdminWithLockById(@Param("id") Long id);

	Optional<Product> findByBarcode(String barcode);

	/** 시드 넣기의 같은 제품 찾기. 기본 collation(utf8mb4_0900_ai_ci)이라 대소문자는 무시된다. 보관한 제품도 돌려준다 */
	List<Product> findByBrandAndNameOrderByIdAsc(String brand, String name);

	/**
	 * 브랜드나 이름에 q가 들어간 제품. 개수는 Pageable로 제한한다(최대 20, 호출하는 쪽에서 정한다).
	 * q의 %, _, !는 호출하는 쪽에서 '!'로 escape해서 넘긴다(MySQL에서 백슬래시는 문자열 escape라 쓰지 않는다)
	 */
	@Query("select p from Product p where p.archived = false and (p.brand like concat('%', :q, '%') escape '!' or p.name like concat('%', :q, '%') escape '!') order by p.id desc")
	List<Product> searchByBrandOrName(@Param("q") String q, Pageable pageable);
}
