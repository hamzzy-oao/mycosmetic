package com.mycosmetic.dto.trial;

import jakarta.validation.constraints.NotNull;

/** POST /api/trials. 내 화장대 항목만 고를 수 있다 */
public record StartTrialRequest(@NotNull Long shelfItemId) {
}
