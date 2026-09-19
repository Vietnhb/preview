package com.example.backend.repository.problem;

import java.util.UUID;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.backend.entity.problem.SourceAsset;
import com.example.backend.entity.account.User;

public interface SourceAssetRepository extends JpaRepository<SourceAsset, UUID> {
    Optional<SourceAsset> findByIdAndSubmissionOwner(UUID id, User owner);
}
