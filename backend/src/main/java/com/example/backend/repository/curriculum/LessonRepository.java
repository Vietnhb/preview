package com.example.backend.repository.curriculum;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.backend.entity.curriculum.Lesson;
import com.example.backend.entity.curriculum.GradeLevel;
import java.util.Optional;

public interface LessonRepository extends JpaRepository<Lesson, UUID> {
    Optional<Lesson> findByLevelAndSlug(GradeLevel level, String slug);
}
