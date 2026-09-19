package com.example.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.backend.entity.SchoolClass;

@Repository
public interface SchoolClassRepository extends JpaRepository<SchoolClass, UUID> {
    
    /**
     * Find all active classes for a school.
     */
    @Query("SELECT c FROM SchoolClass c WHERE c.school.id = :schoolId AND c.isActive = true")
    List<SchoolClass> findBySchoolIdAndIsActiveTrue(@Param("schoolId") UUID schoolId);
    
    /**
     * Find classes by school and year.
     */
    @Query("SELECT c FROM SchoolClass c WHERE c.school.id = :schoolId AND c.schoolYear = :schoolYear AND c.isActive = true")
    List<SchoolClass> findBySchoolIdAndSchoolYear(@Param("schoolId") UUID schoolId, @Param("schoolYear") String schoolYear);

    boolean existsBySchoolIdAndNameIgnoreCaseAndSchoolYear(UUID schoolId, String name, String schoolYear);
}
