package com.example.backend.repository;
import com.example.backend.entity.School;
import org.springframework.data.jpa.repository.JpaRepository;
import java.util.UUID;
public interface SchoolRepository extends JpaRepository<School, UUID> {
    boolean existsByCode(String code);
}
