package com.mycosmetic.dto.trial;

import java.util.Map;

import com.mycosmetic.entity.DiaryMood;
import com.mycosmetic.entity.DiaryTag;

/**
 * 테스트 기간(시작일부터 종료일 또는 오늘까지) 동안의 피부 일기 요약.
 *
 * @param recordedDays 일기를 쓴 날 수
 * @param moods        기분별 일수. 모든 값이 0부터 들어 있다
 * @param tags         태그별 일수. 모든 값이 0부터 들어 있다
 */
public record TrialSummary(int recordedDays, Map<DiaryMood, Long> moods, Map<DiaryTag, Long> tags) {
}
