package com.mycosmetic.repository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mycosmetic.entity.SkinDiaryEntry;

public interface SkinDiaryEntryRepository extends JpaRepository<SkinDiaryEntry, Long> {

	/** 남의 기록은 찾지 못하게 항상 사용자 ID와 함께 조회한다 */
	Optional<SkinDiaryEntry> findByUserIdAndEntryDate(Long userId, LocalDate entryDate);

	/** from·to 포함, 날짜순 */
	List<SkinDiaryEntry> findByUserIdAndEntryDateBetweenOrderByEntryDate(Long userId, LocalDate from, LocalDate to);

	boolean existsByUserIdAndEntryDate(Long userId, LocalDate entryDate);
}
