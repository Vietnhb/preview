package com.example.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

import com.example.backend.entity.ProblemSubmission;
import com.example.backend.entity.User;

public interface ProblemSubmissionRepository extends JpaRepository<ProblemSubmission, UUID> {
    Page<ProblemSubmission> findByOwnerOrderByCreatedAtDesc(User owner, Pageable pageable);

    Optional<ProblemSubmission> findByIdAndOwner(UUID id, User owner);
}
