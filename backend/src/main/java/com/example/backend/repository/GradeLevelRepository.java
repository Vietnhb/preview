package com.example.backend.repository;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.backend.entity.ContentModule;
import com.example.backend.entity.GradeLevel;

public interface GradeLevelRepository extends JpaRepository<GradeLevel, UUID> {
    Optional<GradeLevel> findByModuleAndName(ContentModule module, String name);
}
