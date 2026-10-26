package com.mycosmetic.dto.trial;

import java.time.LocalDate;

import com.mycosmetic.entity.DiaryMood;

/**
 * 테스트 기간의 하루. /trials 화면의 14일 칸.
 *
 * @param mood 그날 피부 일기의 기분. 기록하지 않았거나 아직 오지 않은 날이면 null
 */
public record TrialDay(LocalDate date, DiaryMood mood) {
}
