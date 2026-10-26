package com.mycosmetic.dto.shelf;

import java.time.LocalDate;

import com.mycosmetic.entity.Verdict;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;

/**
 * PATCH /api/shelf/{id}. null인 필드는 바꾸지 않는다.
 *
 * @param memo      빈 문자열(공백만 있는 것 포함)이면 메모를 지운다
 * @param openedOn  개봉일. 오늘(Asia/Seoul)보다 뒤면 INVALID_REQUEST
 * @param paoMonths 개봉 후 사용기한(개월, 1~60)
 * @param shadeName 내 호수 이름(50자 이하). 빈 문자열(공백만 있는 것 포함)이면 지운다
 * @param shadeHex  내 호수 색 #RRGGBB. 빈 문자열이면 지운다. 색조(MAKEUP)가 아닌 항목에 값을 넣으면 INVALID_REQUEST
 * @param stepType   스킨케어 단계(StepType 이름). 빈 문자열이면 지운다. 스킨케어가 아니거나 모르는 값이면 INVALID_ITEM_TYPE
 * @param makeupType 색조 종류(LIP·CHEEK·EYE·BASE). 빈 문자열이면 지운다. 색조가 아니거나 모르는 값이면 INVALID_ITEM_TYPE
 */
public record UpdateShelfRequest(Verdict verdict, @Size(max = 500) String memo, LocalDate startedOn,
		LocalDate endedOn, LocalDate openedOn, @Min(1) @Max(60) Integer paoMonths, @Size(max = 50) String shadeName,
		@Pattern(regexp = "^$|^#[0-9A-Fa-f]{6}$") String shadeHex, @Size(max = 20) String stepType,
		@Size(max = 20) String makeupType) {

	/** 평가·메모·사용 기간만 바꾸는 요청 */
	public UpdateShelfRequest(Verdict verdict, String memo, LocalDate startedOn, LocalDate endedOn) {
		this(verdict, memo, startedOn, endedOn, null, null);
	}

	/** 평가·메모·사용 기간·개봉 정보만 바꾸는 요청 */
	public UpdateShelfRequest(Verdict verdict, String memo, LocalDate startedOn, LocalDate endedOn,
			LocalDate openedOn, Integer paoMonths) {
		this(verdict, memo, startedOn, endedOn, openedOn, paoMonths, null, null);
	}

	/** 제품 종류는 바꾸지 않는 요청 */
	public UpdateShelfRequest(Verdict verdict, String memo, LocalDate startedOn, LocalDate endedOn,
			LocalDate openedOn, Integer paoMonths, String shadeName, String shadeHex) {
		this(verdict, memo, startedOn, endedOn, openedOn, paoMonths, shadeName, shadeHex, null, null);
	}
}
