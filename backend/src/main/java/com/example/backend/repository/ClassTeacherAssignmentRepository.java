package com.example.backend.repository;

import java.util.List;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.example.backend.entity.ClassTeacherAssignment;

@Repository
public interface ClassTeacherAssignmentRepository extends JpaRepository<ClassTeacherAssignment, UUID> {
    
    /**
     * Find all classes assigned to a teacher.
     */
    @Query("SELECT a FROM ClassTeacherAssignment a WHERE a.teacher.id = :teacherId AND a.isActive = true")
    List<ClassTeacherAssignment> findByTeacherIdAndIsActiveTrue(@Param("teacherId") Integer teacherId);
    
    /**
     * Find all teachers assigned to a class.
     */
    @Query("SELECT a FROM ClassTeacherAssignment a WHERE a.schoolClass.id = :classId AND a.isActive = true")
    List<ClassTeacherAssignment> findByClassIdAndIsActiveTrue(@Param("classId") UUID classId);
}
