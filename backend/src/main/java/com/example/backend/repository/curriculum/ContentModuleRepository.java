package com.example.backend.repository.curriculum;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.backend.entity.curriculum.ContentModule;
import com.example.backend.entity.curriculum.Topic;

public interface ContentModuleRepository extends JpaRepository<ContentModule, UUID> {
    Optional<ContentModule> findByTopicAndSlug(Topic topic, String slug);
}
