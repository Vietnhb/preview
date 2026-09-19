package com.example.backend.repository.reviewer;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.backend.entity.reviewer.ReviewerDecision;

public interface ReviewerDecisionRepository extends JpaRepository<ReviewerDecision, UUID> {
}
