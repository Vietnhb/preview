package com.example.backend.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.backend.entity.GoldAnnotation;

public interface GoldAnnotationRepository extends JpaRepository<GoldAnnotation, UUID> {
}
