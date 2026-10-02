package com.example.backend.system.curriculum.repository;

import com.example.backend.system.curriculum.model.entity.ContentModule;
import com.example.backend.system.curriculum.model.entity.Topic;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ContentModuleRepository extends JpaRepository<ContentModule, UUID> {
    Optional<ContentModule> findByTopicAndSlug(Topic topic, String slug);
}
