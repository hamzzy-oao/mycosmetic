package com.mycosmetic.dto.diary;

import java.time.LocalDate;
import java.util.List;

import com.mycosmetic.entity.DiaryMood;
import com.mycosmetic.entity.DiaryTag;
import com.mycosmetic.entity.SkinDiaryEntry;

/** 피부 일기 하루. tags는 DiaryTag 선언 순서 */
public record DiaryEntryResponse(LocalDate date, DiaryMood mood, List<DiaryTag> tags, String memo) {

	public static DiaryEntryResponse of(SkinDiaryEntry entry) {
		return new DiaryEntryResponse(entry.getEntryDate(), entry.getMood(), List.copyOf(entry.getTags()),
				entry.getMemo());
	}
}
