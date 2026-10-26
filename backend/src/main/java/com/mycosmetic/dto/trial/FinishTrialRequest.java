package com.mycosmetic.dto.trial;

import com.mycosmetic.entity.Verdict;

import jakarta.validation.constraints.NotNull;

/** POST /api/trials/{id}/finish. GOOD·BAD만 된다(그 밖은 INVALID_REQUEST) */
public record FinishTrialRequest(@NotNull Verdict verdict) {
}
