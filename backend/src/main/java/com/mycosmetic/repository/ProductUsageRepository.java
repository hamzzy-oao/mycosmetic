package com.mycosmetic.repository;

import java.util.Collection;
import java.util.List;

import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

import com.mycosmetic.entity.ShelfItem;

/** 관리자 점검함: 제품이 얼마나 쓰이는지(몇 명의 화장대에 있는지). 사용자 정보는 내보내지 않고 수만 센다 */
public interface ProductUsageRepository extends Repository<ShelfItem, Long> {

	/** [제품 id, 담은 화장대 항목 수]. 아무도 안 담은 제품은 빠진다 */
	@Query("select s.product.id, count(s) from ShelfItem s where s.product.id in :productIds group by s.product.id")
	List<Object[]> countShelfItemsByProductIdIn(@Param("productIds") Collection<Long> productIds);
}
