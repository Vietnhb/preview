package com.example.backend.system.assignment.dto;

import com.example.backend.system.assignment.model.entity.StudentActionLog;
import com.example.backend.system.assignment.model.enums.AssignmentStatus;
import com.example.backend.system.assignment.model.enums.GradingStatus;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;

/** Requests and responses for learning assignments. */
public final class AssignmentContracts {
    private AssignmentContracts() { }

    public record CreateAssignmentRequest(
            @NotNull UUID libraryItemId,
            UUID classId,
            @NotBlank @Size(max = 160) String title,
            String description,
            @NotNull JsonNode questions,
            @NotEmpty Set<Integer> studentIds,
            Instant dueAt,
            JsonNode gradingCriteria,
            @DecimalMin("0.001") @Digits(integer = 5, fraction = 3) BigDecimal maxScore,
            Boolean autoGrade) {
        public CreateAssignmentRequest(UUID libraryItemId, String title, String description, JsonNode questions,
                                       Set<Integer> studentIds, Instant dueAt) {
            this(libraryItemId, null, title, description, questions, studentIds, dueAt, null, null, false);
        }

        public CreateAssignmentRequest(UUID libraryItemId, String title, String description, JsonNode questions,
                                       Set<Integer> studentIds, Instant dueAt, JsonNode gradingCriteria,
                                       BigDecimal maxScore, Boolean autoGrade) {
            this(libraryItemId, null, title, description, questions, studentIds, dueAt, gradingCriteria, maxScore, autoGrade);
        }
    }

    public record CompleteAssignmentRequest(
            @NotBlank @Size(max = 4000) String conclusion,
            @Size(max = 4000) String answerText,
            Double estimatedValue) {
        public CompleteAssignmentRequest(String conclusion) {
            this(conclusion, null, null);
        }
    }

    public record SubmitPredictionRequest(@NotNull JsonNode predictions) {
    }

    public record GradeAssignmentRequest(
            @NotNull @DecimalMin("0.0") BigDecimal score,
            @Size(max = 4000) String feedback,
            boolean confirm) {
    }

    public record AssignmentResponse(
            UUID id,
            UUID libraryItemId,
            String libraryItemTitle,
            UUID classId,
            String className,
            Integer classGradeLevel,
            UUID specificationId,
            UUID simulationRunId,
            String title,
            String description,
            JsonNode questions,
            Set<Integer> studentIds,
            AssignmentStatus status,
            Instant assignedAt,
            Instant dueAt,
            boolean predictionSubmitted,
            boolean submissionCompleted,
            Instant completedAt,
            JsonNode gradingCriteria,
            BigDecimal maxScore,
            boolean autoGrade,
            BigDecimal score,
            String feedback,
            GradingStatus gradingStatus,
            boolean retryAllowed,
            JsonNode predictions) {
    }

    public record AssignmentSubmissionResponse(UUID id, UUID assignmentId, Integer studentId,
                                               String studentName, JsonNode predictions, Instant submittedAt, Instant completedAt,
                                               BigDecimal score, BigDecimal maxScore, String feedback,
                                               GradingStatus gradingStatus, Instant gradedAt, boolean retryAllowed) {
    }

    public record StudentActionLogRequest(
            @NotNull UUID assignmentId,
            @NotBlank @Size(max = 64) String action,
            JsonNode payload) {
    }

    public record StudentActionLogResponse(UUID id, Instant createdAt, Instant updatedAt, Integer studentId,
                                          UUID assignmentId, String action, JsonNode payload, Instant occurredAt) {
        public static StudentActionLogResponse from(StudentActionLog log) {
            return new StudentActionLogResponse(log.getId(), log.getCreatedAt(), log.getUpdatedAt(), log.getStudentId(),
                    log.getAssignmentId(), log.getAction(), log.getPayload(), log.getOccurredAt());
        }
    }

    public record AssignmentReport(long assigned, long submitted, long pending, long graded, long confirmed,
                                   long retryAllowed, BigDecimal averageScore, BigDecimal maxScore) { }
    public record TeacherClassStudent(Integer id, String fullName) { }
    public record TeacherClassOption(UUID id, String name, Integer gradeLevel, String schoolYear,
                                     String subject, List<TeacherClassStudent> students) { }

    public record SchoolAssignment(UUID id, String title, UUID classId, String className, String schoolYear,
                                   Integer teacherId, String teacherName, int studentCount,
                                   String status, Instant assignedAt, Instant dueAt) { }
}
