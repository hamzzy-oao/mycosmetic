package com.mycosmetic.service.product;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.dao.PessimisticLockingFailureException;
import org.springframework.stereotype.Service;

import com.mycosmetic.dto.product.ConfirmRequest;
import com.mycosmetic.dto.product.ConfirmRequest.NewProduct;
import com.mycosmetic.dto.product.ConfirmResponse;
import com.mycosmetic.dto.product.FinalItem;
import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.exception.ErrorCode;
import com.mycosmetic.service.product.review.ProductNameCheckService;

/**
 * 추출 결과 확인·저장 (설계 4.1 9단계, 4.3).
 * 트랜잭션을 열지 않는다. 두 사용자가 같은 새 제품·버전을 동시에 만들면 중복 불가 제약이 하나만 통과시키고,
 * 진 쪽은 새 트랜잭션으로 <b>1회만</b> 다시 시도해 이미 만들어진 제품·버전에 확인을 더한다(사용자에게 오류를 보여주지 않는다).
 * 다시 시도해도 제약 위반이거나, 잠금 대기 초과·교착(PessimisticLockingFailureException)이 나면
 * 500 대신 409 {@link ErrorCode#CONFIRM_CONFLICT}로 답한다. 화면은 같은 job으로 다시 저장할 수 있다(job은 롤백돼 미확인 상태).
 */
@Service
public class ProductConfirmService {

	private static final Logger log = LoggerFactory.getLogger(ProductConfirmService.class);

	/** dto의 @Size와 같은 값. 컨트롤러를 거치지 않는 호출에서도 지킨다 */
	static final int MAX_ITEMS = 200;
	static final int MAX_RAW_TEXT = 500;

	private final ProductConfirmTxService txService;
	/** 제품명 예외처리(2026-10-26). 없으면(단위 테스트) 같은 이름 확인·링크 출처 표시를 건너뛴다 */
	private final ProductNameCheckService nameCheckService;
	private final ProductLinkService linkService;

	@Autowired
	public ProductConfirmService(ProductConfirmTxService txService, ProductNameCheckService nameCheckService,
			ProductLinkService linkService) {
		this.txService = txService;
		this.nameCheckService = nameCheckService;
		this.linkService = linkService;
	}

	public ProductConfirmService(ProductConfirmTxService txService) {
		this(txService, null, null);
	}

	/**
	 * 새 제품이면 저장 전에 같은 브랜드에 같은 이름(표기 규칙·리필·공백 무시)의 제품이 있는지 본다. 있는데 사용자가
	 * "다른 제품이에요"를 누르지 않았으면 PRODUCT_SAME_NAME_EXISTS. 저장한 뒤 이름이 올리브영 링크 표기 그대로면 출처를 LINK로 한다
	 */
	public ConfirmResponse confirm(Long userId, ConfirmRequest request) {
		validate(request);
		NewProduct newProduct = request.newProduct();
		if (newProduct != null && nameCheckService != null && !newProduct.confirmedDifferent()
				&& blankToNull(newProduct.barcode()) == null
				&& nameCheckService.sameNameProduct(newProduct.brand(), newProduct.name()).isPresent()) {
			throw new BusinessException(ErrorCode.PRODUCT_SAME_NAME_EXISTS);
		}
		// 링크 확인은 쇼핑몰을 한 번 부르므로 트랜잭션 밖에서 미리 한다
		boolean fromLink = newProduct != null && linkService != null && blankToNull(newProduct.sourceUrl()) != null
				&& ProductNameCheckService.sameAsLinkName(newProduct.brand(), newProduct.name(),
						linkService.officialName(newProduct.sourceUrl()));
		ConfirmResponse response = confirmWithRetry(userId, request);
		if (fromLink) {
			nameCheckService.markFromLink(response.productId(), userId);
		}
		return response;
	}

	private ConfirmResponse confirmWithRetry(Long userId, ConfirmRequest request) {
		try {
			return txService.confirmOnce(userId, request);
		} catch (DataIntegrityViolationException e) {
			log.info("동시 확인으로 중복 제약 위반, 다시 조회해서 처리 jobId={}", request.jobId());
			return retryOnce(userId, request);
		} catch (PessimisticLockingFailureException e) {
			log.warn("동시 확인 잠금 실패 jobId={} reason={}", request.jobId(), e.getClass().getSimpleName());
			throw new BusinessException(ErrorCode.CONFIRM_CONFLICT);
		}
	}

	private static String blankToNull(String s) {
		return s == null || s.isBlank() ? null : s.strip();
	}

	private ConfirmResponse retryOnce(Long userId, ConfirmRequest request) {
		try {
			return txService.confirmOnce(userId, request);
		} catch (DataIntegrityViolationException | PessimisticLockingFailureException e) {
			log.warn("동시 확인 재시도 실패 jobId={} reason={}", request.jobId(), e.getClass().getSimpleName());
			throw new BusinessException(ErrorCode.CONFIRM_CONFLICT);
		}
	}

	/** productId와 newProduct 중 정확히 하나, 성분 1~200개, 각 원문은 비어 있지 않고 500자 이하 */
	private static void validate(ConfirmRequest request) {
		if (request.jobId() == null || (request.productId() == null) == (request.newProduct() == null)) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST);
		}
		if (request.items() == null || request.items().isEmpty() || request.items().size() > MAX_ITEMS) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST);
		}
		for (FinalItem item : request.items()) {
			if (item == null || item.rawText() == null || item.rawText().isBlank()
					|| item.rawText().strip().length() > MAX_RAW_TEXT) {
				throw new BusinessException(ErrorCode.INVALID_REQUEST);
			}
		}
		if (request.newProduct() != null && (request.newProduct().brand() == null
				|| request.newProduct().brand().isBlank() || request.newProduct().name() == null
				|| request.newProduct().name().isBlank() || request.newProduct().category() == null)) {
			throw new BusinessException(ErrorCode.INVALID_REQUEST);
		}
	}
}
