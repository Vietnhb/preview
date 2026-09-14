package com.example.backend.repository;

import java.util.UUID;
import java.util.Optional;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.backend.entity.SourceAsset;
import com.example.backend.entity.User;

public interface SourceAssetRepository extends JpaRepository<SourceAsset, UUID> {
    Optional<SourceAsset> findByIdAndSubmissionOwner(UUID id, User owner);
}
