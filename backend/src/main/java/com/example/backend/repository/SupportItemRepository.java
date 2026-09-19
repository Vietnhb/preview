package com.example.backend.repository;

import com.example.backend.entity.SupportItem;
import com.example.backend.enums.SupportKind;
import com.example.backend.enums.SupportStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SupportItemRepository extends JpaRepository<SupportItem, UUID> {
    List<SupportItem> findTop200ByKindOrderByCreatedAtDesc(SupportKind kind);
    List<SupportItem> findBySenderIdOrderByCreatedAtDesc(Integer senderId);
    long countByKindAndStatus(SupportKind kind, SupportStatus status);
}
