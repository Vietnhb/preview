package com.example.backend.system.problem.repository;

import com.example.backend.system.account.model.entity.User;
import com.example.backend.system.problem.model.entity.Specification;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SpecificationRepository extends JpaRepository<Specification, UUID> {
    Optional<Specification> findByIdAndSubmissionOwner(UUID id, User owner);

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @org.springframework.data.jpa.repository.Query("select s from Specification s where s.id = :id and s.submission.owner = :owner")
    Optional<Specification> lockOwned(UUID id, User owner);
}
