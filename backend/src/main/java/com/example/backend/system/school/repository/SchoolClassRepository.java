package com.example.backend.system.school.repository;

import com.example.backend.system.school.model.entity.SchoolClass;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

@Repository
public interface SchoolClassRepository extends JpaRepository<SchoolClass, UUID> {
    
    /**
     * Find all active classes for a school.
     */
    @Query("SELECT c FROM SchoolClass c WHERE c.school.id = :schoolId AND c.isActive = true")
    List<SchoolClass> findBySchoolIdAndIsActiveTrue(@Param("schoolId") UUID schoolId);
    
    boolean existsBySchoolIdAndNameIgnoreCaseAndSchoolYear(UUID schoolId, String name, String schoolYear);
}
