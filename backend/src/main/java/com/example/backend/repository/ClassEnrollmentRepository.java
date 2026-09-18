package com.example.backend.repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.backend.entity.ClassEnrollment;
import com.example.backend.entity.ClassEnrollment.EnrollmentStatus;

@Repository
public interface ClassEnrollmentRepository extends JpaRepository<ClassEnrollment, UUID> {
    
    /**
     * Find student's active enrollment in a specific school year.
     */
    @Query("SELECT e FROM ClassEnrollment e WHERE e.student.id = :studentId AND e.schoolYear = :schoolYear AND e.status = 'ACTIVE'")
    Optional<ClassEnrollment> findActiveEnrollment(@Param("studentId") Integer studentId, @Param("schoolYear") String schoolYear);
    
    /**
     * Find all enrollments for a class.
     */
    @Query("SELECT e FROM ClassEnrollment e WHERE e.schoolClass.id = :classId")
    List<ClassEnrollment> findByClassId(@Param("classId") UUID classId);
    
    /**
     * Find all active students in a class.
     */
    @Query("SELECT e FROM ClassEnrollment e WHERE e.schoolClass.id = :classId AND e.status = 'ACTIVE'")
    List<ClassEnrollment> findActiveStudentsByClassId(@Param("classId") UUID classId);
    
    /**
     * Count active students in a class.
     */
    @Query("SELECT COUNT(e) FROM ClassEnrollment e WHERE e.schoolClass.id = :classId AND e.status = 'ACTIVE'")
    Long countActiveStudentsByClassId(@Param("classId") UUID classId);
}
