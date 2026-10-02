package com.example.backend.system.problem.repository;

import com.example.backend.system.account.model.entity.User;
import com.example.backend.system.problem.model.entity.ProblemSubmission;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ProblemSubmissionRepository extends JpaRepository<ProblemSubmission, UUID> {
    Page<ProblemSubmission> findByOwnerOrderByCreatedAtDesc(User owner, Pageable pageable);

    Optional<ProblemSubmission> findByIdAndOwner(UUID id, User owner);
}
