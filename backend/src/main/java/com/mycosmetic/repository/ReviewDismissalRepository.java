package com.mycosmetic.repository;

import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.mycosmetic.entity.ReviewDismissal;

public interface ReviewDismissalRepository extends JpaRepository<ReviewDismissal, Long> {

	Optional<ReviewDismissal> findByProductIdAndIssueType(Long productId, String issueType);
}
