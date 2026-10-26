package com.mycosmetic.dto.routine;

import com.mycosmetic.entity.StepType;

import jakarta.validation.constraints.NotNull;

/** 루틴 단계 하나. 내 화장대 항목만 넣을 수 있다(남의 항목은 404) */
public record StepRequest(@NotNull Long shelfItemId, @NotNull StepType stepType) {
}
