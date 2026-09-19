package com.example.backend.repository.school;

import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.stereotype.Repository;

import com.example.backend.entity.school.School;

@Repository
public interface SchoolRepository extends JpaRepository<School, UUID> {

    @org.springframework.data.jpa.repository.Lock(jakarta.persistence.LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT s FROM School s WHERE s.id = :id")
    Optional<School> findByIdForUpdate(@org.springframework.data.repository.query.Param("id") UUID id);

    Optional<School> findByCode(String code);

    boolean existsByCode(String code);

    Optional<School> findByName(String name);

}
