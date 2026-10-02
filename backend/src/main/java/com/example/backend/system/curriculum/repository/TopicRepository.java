package com.example.backend.system.curriculum.repository;

import com.example.backend.system.curriculum.model.entity.Topic;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface TopicRepository extends JpaRepository<Topic, UUID> {
    Optional<Topic> findBySlug(String slug);
    List<Topic> findByNameIgnoreCaseAndEnabledTrueOrderBySortOrderAsc(String name);

    List<Topic> findAllByOrderBySortOrderAsc();

    List<Topic> findByEnabledTrueOrderBySortOrderAsc();

    boolean existsByNameIgnoreCaseAndEnabledTrue(String name);
}
