package com.example.backend.repository;

import java.util.Optional;
import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.backend.entity.AmbiguityCase;
import com.example.backend.enums.AmbiguityStatus;
import com.example.backend.entity.Specification;

public interface AmbiguityCaseRepository extends JpaRepository<AmbiguityCase, UUID> {
    List<AmbiguityCase> findByStatus(AmbiguityStatus status);
    Optional<AmbiguityCase> findByIdAndSpecification(UUID id, Specification specification);
}
