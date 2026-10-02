package com.example.backend.system.problem.repository;

import com.example.backend.system.account.model.entity.User;
import com.example.backend.system.problem.model.entity.SourceAsset;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SourceAssetRepository extends JpaRepository<SourceAsset, UUID> {
    Optional<SourceAsset> findByIdAndSubmissionOwner(UUID id, User owner);
}
