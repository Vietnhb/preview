package com.example.backend.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.backend.entity.Lesson;
import com.example.backend.entity.GradeLevel;
import java.util.Optional;

public interface LessonRepository extends JpaRepository<Lesson, UUID> {
    Optional<Lesson> findByLevelAndSlug(GradeLevel level, String slug);
}
