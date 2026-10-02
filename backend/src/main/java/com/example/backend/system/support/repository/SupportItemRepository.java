package com.example.backend.system.support.repository;

import com.example.backend.system.support.model.entity.SupportItem;
import com.example.backend.system.support.model.enums.SupportKind;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface SupportItemRepository extends JpaRepository<SupportItem, UUID> {
    List<SupportItem> findTop200ByKindOrderByCreatedAtDesc(SupportKind kind);
    List<SupportItem> findBySenderIdOrderByCreatedAtDesc(Integer senderId);
}
