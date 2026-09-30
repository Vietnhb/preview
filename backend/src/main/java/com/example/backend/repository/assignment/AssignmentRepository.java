package com.example.backend.repository.assignment;

import com.example.backend.entity.assignment.Assignment;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

import java.util.List;
import java.util.UUID;

public interface AssignmentRepository extends JpaRepository<Assignment, UUID> {
    List<Assignment> findByTeacherIdOrderByCreatedAtDesc(Integer teacherId);
    List<Assignment> findByAssignedStudentIdsContaining(Integer studentId);

    @EntityGraph(attributePaths = {"teacher", "schoolClass"})
    @Query("select a from Assignment a left join a.schoolClass c where c.school.id = :schoolId "
            + "or (c is null and a.teacher.school.id = :schoolId)")
    Page<Assignment> findSchoolAssignments(UUID schoolId, Pageable pageable);
}
