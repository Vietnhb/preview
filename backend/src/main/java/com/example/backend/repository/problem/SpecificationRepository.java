package com.example.backend.repository.problem;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.backend.entity.problem.Specification;
import com.example.backend.entity.account.User;

public interface SpecificationRepository extends JpaRepository<Specification, UUID> {
    Optional<Specification> findByIdAndSubmissionOwner(UUID id, User owner);
}
