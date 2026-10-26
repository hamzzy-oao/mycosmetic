package com.mycosmetic.repository;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mycosmetic.entity.ListConfirmation;

public interface ListConfirmationRepository extends JpaRepository<ListConfirmation, Long> {

	boolean existsByListIdAndUserId(Long listId, Long userId);

	long countByListId(Long listId);
}
