package com.example.backend.repository;

import com.example.backend.entity.TopicModuleRelease;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface TopicModuleReleaseRepository extends JpaRepository<TopicModuleRelease, UUID> {
    List<TopicModuleRelease> findByTopicIgnoreCaseOrderByCreatedAtDesc(String topic);
}
