package com.example.backend.system.curriculum.repository;

import com.example.backend.system.curriculum.model.entity.GradeLevel;
import com.example.backend.system.curriculum.model.entity.Lesson;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LessonRepository extends JpaRepository<Lesson, UUID> {
    Optional<Lesson> findByLevelAndSlug(GradeLevel level, String slug);
}
