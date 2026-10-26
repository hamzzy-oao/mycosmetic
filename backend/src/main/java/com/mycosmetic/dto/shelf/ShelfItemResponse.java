package com.mycosmetic.dto.shelf;

import java.time.Instant;
import java.time.LocalDate;
import java.util.List;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.mycosmetic.dto.product.ProductSummaryResponse;
import com.mycosmetic.entity.MakeupType;
import com.mycosmetic.entity.StepType;
import com.mycosmetic.entity.Verdict;

/**
 * 내 화장대 항목.
 *
 * @param ingredientListId     내가 확인한(담은) 성분 버전. 대표 버전이 바뀌어도 그대로다(설계 3.5-5)
 * @param listIsRepresentative false면 화면이 "새 성분 버전이 있어요"를 보여준다
 * @param paoMonths            개봉 후 사용기한(개월)
 * @param expiresOn            개봉일 + 사용기한. 둘 중 하나라도 없으면 null
 * @param usageDays            시작일부터 종료일(없으면 오늘, Asia/Seoul)까지 일수. 시작일이 없으면 null
 * @param shadeName            내 호수 이름
 * @param shadeHex             내 호수 색 #RRGGBB(대문자)
 * @param shadeTone            색 판정(참고용). 색이 없으면 null
 * @param shadeSeason          계절 판정(참고용). 색이 없거나 NEUTRAL이면 null
 * @param shadeMatchesMe       내 퍼스널컬러와 웜/쿨이 같은지. 색이나 퍼스널컬러가 없으면 null
 * @param stepType             저장한 스킨케어 단계. 없으면 null(화장대 분석이 이름으로 추정한다)
 * @param makeupType           저장한 색조 종류. 없으면 null
 * @param matchedIngredients   성분으로 찾기(GET /api/shelf?ingredient=)에서 일치한 성분 이름. 그 외 응답에는 없다
 */
public record ShelfItemResponse(Long id, ProductSummaryResponse product, Long ingredientListId,
		boolean listIsRepresentative, Verdict verdict, String memo, LocalDate startedOn, LocalDate endedOn,
		LocalDate openedOn, Integer paoMonths, LocalDate expiresOn, ExpiryStatus expiryStatus, Long usageDays,
		String shadeName, String shadeHex, ShadeTone shadeTone, ShadeSeason shadeSeason, Boolean shadeMatchesMe,
		StepType stepType, MakeupType makeupType, Instant updatedAt, @JsonInclude(JsonInclude.Include.NON_NULL) List<String> matchedIngredients) {

	/** 같은 항목에 일치한 성분 이름을 붙인 사본 */
	public ShelfItemResponse withMatchedIngredients(List<String> names) {
		return new ShelfItemResponse(id, product, ingredientListId, listIsRepresentative, verdict, memo, startedOn,
				endedOn, openedOn, paoMonths, expiresOn, expiryStatus, usageDays, shadeName, shadeHex, shadeTone,
				shadeSeason, shadeMatchesMe, stepType, makeupType, updatedAt, List.copyOf(names));
	}
}
