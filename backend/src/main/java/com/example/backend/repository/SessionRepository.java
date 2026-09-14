package com.example.backend.repository;

import com.example.backend.entity.Session;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

public interface SessionRepository extends JpaRepository<Session, UUID> {
    List<Session> findByTeacherIdOrderByCreatedAtDesc(Integer teacherId);
}
