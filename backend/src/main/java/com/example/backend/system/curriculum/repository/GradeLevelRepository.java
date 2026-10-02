package com.example.backend.system.curriculum.repository;

import com.example.backend.system.curriculum.model.entity.ContentModule;
import com.example.backend.system.curriculum.model.entity.GradeLevel;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;

public interface GradeLevelRepository extends JpaRepository<GradeLevel, UUID> {
    Optional<GradeLevel> findByModuleAndName(ContentModule module, String name);
}
