package com.example.backend.repository.support;

import com.example.backend.entity.support.SupportItem;
import com.example.backend.entity.enums.SupportKind;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SupportItemRepository extends JpaRepository<SupportItem, UUID> {
    List<SupportItem> findTop200ByKindOrderByCreatedAtDesc(SupportKind kind);
    List<SupportItem> findBySenderIdOrderByCreatedAtDesc(Integer senderId);
}
