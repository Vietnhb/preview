package com.example.backend.repository.school;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.backend.entity.school.ClassTeacherAssignment;

@Repository
public interface ClassTeacherAssignmentRepository extends JpaRepository<ClassTeacherAssignment, UUID> {
    
    /**
     * Find all teachers assigned to a class.
     */
    @Query("SELECT a FROM ClassTeacherAssignment a WHERE a.schoolClass.id = :classId AND a.isActive = true")
    List<ClassTeacherAssignment> findByClassIdAndIsActiveTrue(@Param("classId") UUID classId);

    boolean existsBySchoolClassIdAndTeacherIdAndIsActiveTrue(UUID classId, Integer teacherId);

    java.util.Optional<ClassTeacherAssignment> findBySchoolClassIdAndTeacherId(UUID classId, Integer teacherId);
}
