package com.example.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.backend.entity.ContentModule;
import com.example.backend.entity.Topic;

public interface ContentModuleRepository extends JpaRepository<ContentModule, UUID> {
    Optional<ContentModule> findByTopicAndSlug(Topic topic, String slug);
}
