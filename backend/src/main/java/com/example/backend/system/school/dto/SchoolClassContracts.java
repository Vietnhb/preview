package com.example.backend.system.school.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.util.List;
import java.util.UUID;

/** Requests and responses for school operations. */
public final class SchoolClassContracts {
    private SchoolClassContracts() { }

    public record SchoolClassRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull @Min(10) @Max(12) Integer gradeLevel,
            @NotBlank @Size(max = 20) @Pattern(regexp = "\\d{4}-\\d{4}") String schoolYear,
            @Size(max = 50) String subject) {
    }

    public record AssignTeacherRequest(@NotNull Integer teacherId) {
    }

    public record EnrollStudentRequest(@NotNull Integer studentId) {
    }

    public record StudentOptionResponse(Integer id, String fullName) {
    }

    public record Person(Integer id, String fullName, String email) { }
    public record ClassSummary(UUID id, String name, Integer gradeLevel, String schoolYear, String subject,
                               boolean active, long teacherCount, long studentCount) { }
    public record ClassDetail(UUID id, String name, Integer gradeLevel, String schoolYear, String subject,
                              boolean active, List<Person> teachers, List<Person> students) { }
    public record TeacherAssignment(UUID id, Integer teacherId, String teacherName, boolean active) { }
    public record Enrollment(UUID id, Integer studentId, String studentName, String schoolYear, String status) { }
    public record StudentClassTeacher(Integer id, String fullName) { }
    public record StudentClassSummary(UUID id, String name, Integer gradeLevel, String schoolYear, String subject,
                                      UUID schoolId, String schoolName, List<StudentClassTeacher> teachers, long classmateCount) { }
}
