package com.example.backend.repository.curriculum;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.backend.entity.curriculum.ContentModule;
import com.example.backend.entity.curriculum.GradeLevel;

public interface GradeLevelRepository extends JpaRepository<GradeLevel, UUID> {
    Optional<GradeLevel> findByModuleAndName(ContentModule module, String name);
}
