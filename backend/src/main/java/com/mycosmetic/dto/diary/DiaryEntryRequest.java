package com.mycosmetic.dto.diary;

import java.util.Set;

import com.mycosmetic.entity.DiaryMood;
import com.mycosmetic.entity.DiaryTag;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

/**
 * PUT /api/diary/{date}. 그날 기록을 통째로 바꾼다.
 *
 * @param tags 비었거나 없으면 태그 없음
 * @param memo 200자 이하. 빈 문자열(공백만 있는 것 포함)이면 메모 없음
 */
public record DiaryEntryRequest(@NotNull DiaryMood mood, Set<DiaryTag> tags, @Size(max = 200) String memo) {
}
