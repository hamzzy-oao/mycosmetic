package com.mycosmetic.controller;

import java.util.List;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.mycosmetic.dto.product.ConfirmRequest;
import com.mycosmetic.dto.product.ConfirmResponse;
import com.mycosmetic.dto.product.ListDiffResponse;
import com.mycosmetic.dto.product.ProductDetailResponse;
import com.mycosmetic.dto.product.ProductDetailResponse.ListView;
import com.mycosmetic.dto.product.ProductSummaryResponse;
import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.security.CurrentUserId;
import com.mycosmetic.service.product.ListDiffService;
import com.mycosmetic.service.product.ProductConfirmService;
import com.mycosmetic.service.product.ProductQueryService;

import jakarta.validation.Valid;

@RestController
@RequestMapping("/api/products")
public class ProductController {

	/** 이름 검색어 최대 길이. 넘으면 400(긴 LIKE 검색으로 DB를 붙잡지 않게) */
	static final int MAX_QUERY_LENGTH = 100;

	private final ProductConfirmService confirmService;
	private final ProductQueryService queryService;
	private final ListDiffService diffService;

	public ProductController(ProductConfirmService confirmService, ProductQueryService queryService,
			ListDiffService diffService) {
		this.confirmService = confirmService;
		this.queryService = queryService;
		this.diffService = diffService;
	}

	/** q로 브랜드·제품명 검색(목록). q가 없으면 400. 바코드 조회는 2026-10-23 사용자 결정으로 뺐다 */
	@GetMapping
	ResponseEntity<?> find(@RequestParam(name = "q", required = false) String q) {
		if (q != null) {
			if (q.length() > MAX_QUERY_LENGTH) {
				throw new BusinessException(ErrorCode.INVALID_REQUEST);
			}
			List<ProductSummaryResponse> products = queryService.search(q);
			return ResponseEntity.ok(products);
		}
		throw new BusinessException(ErrorCode.INVALID_REQUEST);
	}

	/** 제품 상세: 대표 버전과 나머지 버전 목록 */
	@GetMapping("/{id}")
	ProductDetailResponse detail(@PathVariable("id") Long id) {
		return queryService.detail(id);
	}

	/** 특정 성분 버전과 성분 줄. 그 제품의 버전이 아니면 404 */
	@GetMapping("/{id}/lists/{listId}")
	ListView list(@PathVariable("id") Long id, @PathVariable("listId") Long listId) {
		return queryService.list(id, listId);
	}

	/** 같은 제품의 두 성분 버전 차이 */
	@GetMapping("/{id}/lists/diff")
	ListDiffResponse diff(@PathVariable("id") Long id, @RequestParam("from") Long from, @RequestParam("to") Long to) {
		return diffService.diff(id, from, to);
	}

	/** 추출 결과 확인·저장. 제품·성분 버전을 찾거나 만들고 내 화장대에 담는다 */
	@PostMapping("/confirm")
	ResponseEntity<ConfirmResponse> confirm(@CurrentUserId Long userId, @Valid @RequestBody ConfirmRequest request) {
		return ResponseEntity.status(HttpStatus.CREATED).body(confirmService.confirm(userId, request));
	}
}
