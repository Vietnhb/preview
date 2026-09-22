package com.example.backend.repository.problem;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.backend.entity.problem.Specification;
import com.example.backend.entity.account.User;

public interface SpecificationRepository extends JpaRepository<Specification, UUID> {
    Optional<Specification> findByIdAndSubmissionOwner(UUID id, User owner);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from Specification s where s.id = :id and s.submission.owner = :owner")
    Optional<Specification> lockOwned(UUID id, User owner);
}
