package com.example.backend.service.assignment;

import com.example.backend.dto.assignment.CreateAssignmentRequest;
import com.example.backend.dto.assignment.SubmitPredictionRequest;
import com.example.backend.entity.account.Role;
import com.example.backend.entity.account.User;
import com.example.backend.entity.assignment.Assignment;
import com.example.backend.entity.assignment.AssignmentSubmission;
import com.example.backend.entity.enums.AssignmentStatus;
import com.example.backend.entity.enums.GradingStatus;
import com.example.backend.entity.problem.Specification;
import com.example.backend.exception.ApiException;
import com.example.backend.repository.assignment.AssignmentRepository;
import com.example.backend.repository.assignment.AssignmentSubmissionRepository;
import com.example.backend.service.account.CurrentUserService;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class AssignmentServiceTest {
    private final ObjectMapper json = new ObjectMapper();
    private final AssignmentRepository assignments = mock(AssignmentRepository.class);
    private final AssignmentSubmissionRepository submissions = mock(AssignmentSubmissionRepository.class);
    private final CurrentUserService currentUser = mock(CurrentUserService.class);
    private final AssignmentService service = new AssignmentService(assignments, submissions, null, null, currentUser, null, null, null);
    private final UUID id = UUID.randomUUID();
    private Assignment assignment;
    private User student;

    @BeforeEach
    void setup() {
        student = new User(); student.setId(7); student.setFullName("Student");
        Role role = new Role(); role.setName("STUDENT"); student.setRole(role);
        when(currentUser.requireCurrentUser()).thenReturn(student);
        assignment = new Assignment(); assignment.setId(id); assignment.setAssignedStudentIds(Set.of(7));
        assignment.setSpecification(new Specification());
        when(assignments.findById(id)).thenReturn(Optional.of(assignment));
        when(submissions.save(any())).thenAnswer(call -> call.getArgument(0));
    }

    @Test
    void studentResponseIncludesOwnAnswerButNeverAnswerKey() {
        assignment.setGradingCriteria(json.createObjectNode().put("expectedValue", 42).put("tolerance", 0));
        AssignmentSubmission own = new AssignmentSubmission();
        own.setPredictions(json.createObjectNode().put("answerText", "My answer"));
        when(assignments.findByAssignedStudentIdsContaining(7)).thenReturn(List.of(assignment));
        when(submissions.findByAssignmentIdAndStudentId(id, 7)).thenReturn(Optional.of(own));
        var result = service.forStudent().getFirst();
        assertNull(result.gradingCriteria());
        assertEquals("My answer", result.predictions().path("answerText").asText());
    }

    @Test
    void numericGradingRequiresAnActualNumber() {
        assignment.setAutoGrade(true);
        assertThrows(ApiException.class, () -> service.submit(id, new SubmitPredictionRequest(
                json.createObjectNode().put("answerText", "42").put("estimatedValue", "not a number"))));
        verify(submissions, never()).save(any());
    }

    @Test
    void returnedWorkCanBeResubmittedAndRegraded() {
        assignment.setAutoGrade(true);
        assignment.setGradingCriteria(json.createObjectNode().put("expectedValue", 42).put("tolerance", 0.5));
        AssignmentSubmission own = new AssignmentSubmission(); own.setRetryAllowed(true);
        own.setScore(BigDecimal.ZERO); own.setFeedback("Try again");
        when(submissions.findByAssignmentIdAndStudentId(id, 7)).thenReturn(Optional.of(own));
        var result = service.submit(id, new SubmitPredictionRequest(json.createObjectNode()
                .put("answerText", "Revised answer").put("estimatedValue", 42.25)));
        assertEquals(BigDecimal.TEN, result.score());
        assertEquals(GradingStatus.AI_GRADED, result.gradingStatus());
        assertFalse(result.retryAllowed());
        assertNull(result.feedback());
    }

    @Test
    void submittedWorkCannotBeOverwrittenWithoutReopening() {
        when(submissions.findByAssignmentIdAndStudentId(id, 7)).thenReturn(Optional.of(new AssignmentSubmission()));
        assertThrows(ApiException.class, () -> service.submit(id, new SubmitPredictionRequest(
                json.createObjectNode().put("answerText", "Changed answer"))));
        verify(submissions, never()).save(any());
    }

    @Test
    void closedAssignmentsRejectSubmission() {
        assignment.setStatus(AssignmentStatus.CLOSED);
        assertThrows(ApiException.class, () -> service.submit(id, new SubmitPredictionRequest(
                json.createObjectNode().put("answerText", "Answer"))));
        verify(submissions, never()).save(any());
    }

    @Test
    void newAssignmentsRejectPastDeadlineAndInvalidGradingCriteria() {
        var question = json.createObjectNode().put("prompt", "Predict velocity");
        assertThrows(ApiException.class, () -> service.create(new CreateAssignmentRequest(
                UUID.randomUUID(), "Test", null, question, Set.of(7), Instant.now().minusSeconds(60))));
        assertThrows(ApiException.class, () -> service.create(new CreateAssignmentRequest(
                UUID.randomUUID(), "Test", null, question, Set.of(7), null,
                json.createObjectNode().put("expectedValue", 10).put("tolerance", -1), BigDecimal.TEN, true)));
        verify(assignments, never()).save(any());
    }
}
