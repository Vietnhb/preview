package com.example.backend.repository;

import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;

import com.example.backend.entity.Adjudication;

public interface AdjudicationRepository extends JpaRepository<Adjudication, UUID> {
}
