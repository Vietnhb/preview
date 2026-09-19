package com.example.backend.repository.curriculum;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.backend.entity.curriculum.Topic;

public interface TopicRepository extends JpaRepository<Topic, UUID> {
    Optional<Topic> findBySlug(String slug);

    List<Topic> findAllByOrderBySortOrderAsc();
}
