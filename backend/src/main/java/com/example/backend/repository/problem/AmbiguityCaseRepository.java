package com.example.backend.repository.problem;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.backend.entity.problem.AmbiguityCase;
import com.example.backend.entity.enums.AmbiguityStatus;
import com.example.backend.entity.problem.Specification;

public interface AmbiguityCaseRepository extends JpaRepository<AmbiguityCase, UUID> {
    List<AmbiguityCase> findByStatus(AmbiguityStatus status);
    Optional<AmbiguityCase> findByIdAndSpecification(UUID id, Specification specification);
}
