package com.mycosmetic.service.product;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.List;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.dao.CannotAcquireLockException;
import org.springframework.dao.DataIntegrityViolationException;

import com.mycosmetic.dto.product.ConfirmRequest;
import com.mycosmetic.dto.product.ConfirmResponse;
import com.mycosmetic.dto.product.FinalItem;
import com.mycosmetic.entity.ListStatus;
import com.mycosmetic.exception.BusinessException;
import com.mycosmetic.exception.ErrorCode;

/** 설계 4.3: 중복 불가 제약 위반은 새 트랜잭션으로 1회만 다시 시도한다 */
class ProductConfirmServiceRetryTest {

	final ProductConfirmTxService txService = mock(ProductConfirmTxService.class);
	final ProductConfirmService service = new ProductConfirmService(txService);
	final ConfirmRequest request = new ConfirmRequest(1L, 2L, null, List.of(new FinalItem(null, "a")));

	@Test
	@DisplayName("중복 제약 위반이 한 번 나면 다시 불러 성공한다")
	void retriesOnceOnDuplicate() {
		ConfirmResponse ok = new ConfirmResponse(2L, 3L, ListStatus.VERIFIED, 4L);
		when(txService.confirmOnce(7L, request))
				.thenThrow(new DataIntegrityViolationException("dup"))
				.thenReturn(ok);

		assertThat(service.confirm(7L, request)).isEqualTo(ok);
		verify(txService, times(2)).confirmOnce(7L, request);
	}

	@Test
	@DisplayName("두 번째도 실패하면 더 시도하지 않고 409 CONFIRM_CONFLICT")
	void givesUpAfterSecondFailure() {
		when(txService.confirmOnce(7L, request))
				.thenThrow(new DataIntegrityViolationException("dup"))
				.thenThrow(new DataIntegrityViolationException("dup again"));

		assertThatThrownBy(() -> service.confirm(7L, request)).isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.CONFIRM_CONFLICT);
		verify(txService, times(2)).confirmOnce(7L, request);
	}

	@Test
	@DisplayName("잠금 대기 초과·교착(CannotAcquireLockException)은 500 대신 409 CONFIRM_CONFLICT")
	void lockFailureBecomesConflict() {
		when(txService.confirmOnce(7L, request)).thenThrow(new CannotAcquireLockException("deadlock"));

		assertThatThrownBy(() -> service.confirm(7L, request)).isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.CONFIRM_CONFLICT);
	}

	@Test
	@DisplayName("다시 시도하는 중 잠금 실패가 나도 409 CONFIRM_CONFLICT")
	void lockFailureOnRetryBecomesConflict() {
		when(txService.confirmOnce(7L, request))
				.thenThrow(new DataIntegrityViolationException("dup"))
				.thenThrow(new CannotAcquireLockException("deadlock"));

		assertThatThrownBy(() -> service.confirm(7L, request)).isInstanceOf(BusinessException.class)
				.extracting("errorCode").isEqualTo(ErrorCode.CONFIRM_CONFLICT);
	}
}
