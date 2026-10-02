package com.example.backend.system.reviewer.repository;

import com.example.backend.system.reviewer.model.entity.ReviewerDecision;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ReviewerDecisionRepository extends JpaRepository<ReviewerDecision, UUID> {
}
