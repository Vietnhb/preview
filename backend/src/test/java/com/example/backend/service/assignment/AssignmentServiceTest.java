package com.example.backend.service.assignment;

import com.example.backend.dto.assignment.CreateAssignmentRequest;
import com.example.backend.dto.assignment.SubmitPredictionRequest;
import com.example.backend.dto.assignment.CompleteAssignmentRequest;
import com.example.backend.entity.account.Role;
import com.example.backend.entity.account.User;
import com.example.backend.entity.assignment.Assignment;
import com.example.backend.entity.assignment.AssignmentSubmission;
import com.example.backend.entity.enums.AssignmentStatus;
import com.example.backend.entity.enums.GradingStatus;
import com.example.backend.entity.problem.Specification;
import com.example.backend.entity.library.LibraryItem;
import com.example.backend.entity.simulation.Simulation;
import com.example.backend.exception.ApiException;
import com.example.backend.repository.assignment.AssignmentRepository;
import com.example.backend.repository.assignment.AssignmentSubmissionRepository;
import com.example.backend.repository.school.ClassEnrollmentRepository;
import com.example.backend.repository.school.ClassTeacherAssignmentRepository;
import com.example.backend.entity.school.ClassEnrollment;
import com.example.backend.entity.school.ClassTeacherAssignment;
import com.example.backend.entity.school.SchoolClass;
import com.example.backend.service.account.CurrentUserService;
import com.example.backend.service.simulation.SimulationService;
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
        service.submit(id, new SubmitPredictionRequest(json.createObjectNode()
                .put("answerText", "Revised answer").put("estimatedValue", 42.25)));
        var result = service.complete(id, new CompleteAssignmentRequest("The simulation confirms my prediction"));
        assertEquals(BigDecimal.TEN, result.score());
        assertEquals(GradingStatus.AI_GRADED, result.gradingStatus());
        assertFalse(result.retryAllowed());
        assertNull(result.feedback());
    }

    @Test
    void predictionDoesNotCountAsCompletedUntilStudentHandsInConclusion() {
        AssignmentSubmission own = new AssignmentSubmission();
        own.setRetryAllowed(true);
        when(submissions.findByAssignmentIdAndStudentId(id, 7)).thenReturn(Optional.of(own));

        var prediction = service.submit(id, new SubmitPredictionRequest(
                json.createObjectNode().put("answerText", "It speeds up")));
        assertNull(prediction.completedAt());

        var completed = service.complete(id, new CompleteAssignmentRequest("The velocity increased linearly."));
        assertNotNull(completed.completedAt());
        assertEquals("The velocity increased linearly.", completed.predictions().path("conclusion").asText());
    }

    @Test
    void freeExplorationCanBeCompletedWithoutAPredictionStep() {
        assignment.setQuestions(json.createObjectNode()
                .put("prompt", "Explore the simulation and explain what you observe")
                .put("activityType", "FREE_EXPLORATION"));
        when(submissions.findByAssignmentIdAndStudentId(id, 7)).thenReturn(Optional.empty());

        var completed = service.complete(id, new CompleteAssignmentRequest(
                "Increasing the initial velocity makes the object travel farther.",
                "Increasing the initial velocity makes the object travel farther.",
                null));

        assertNotNull(completed.completedAt());
        assertEquals("Increasing the initial velocity makes the object travel farther.",
                completed.predictions().path("answerText").asText());
        verify(submissions).save(any(AssignmentSubmission.class));
    }

    @Test
    void teacherClassOptionsContainOnlyStudentsInAssignedClasses() {
        ClassEnrollmentRepository enrollments = mock(ClassEnrollmentRepository.class);
        ClassTeacherAssignmentRepository teacherClasses = mock(ClassTeacherAssignmentRepository.class);
        AssignmentService teacherService = new AssignmentService(assignments, submissions, null, null,
                currentUser, null, enrollments, teacherClasses);
        User teacher = new User(); teacher.setId(12); teacher.setFullName("Teacher");
        Role teacherRole = new Role(); teacherRole.setName("TEACHER"); teacher.setRole(teacherRole);
        when(currentUser.requireCurrentUser()).thenReturn(teacher);
        SchoolClass schoolClass = new SchoolClass(); schoolClass.setId(UUID.randomUUID());
        schoolClass.setName("12A1"); schoolClass.setGradeLevel(12); schoolClass.setSchoolYear("2026-2027");
        ClassTeacherAssignment teaching = new ClassTeacherAssignment(); teaching.setSchoolClass(schoolClass); teaching.setTeacher(teacher);
        ClassEnrollment enrollment = new ClassEnrollment(); enrollment.setSchoolClass(schoolClass); enrollment.setStudent(student);
        when(teacherClasses.findActiveByTeacherId(12)).thenReturn(List.of(teaching));
        when(enrollments.findActiveStudentsByClassId(schoolClass.getId())).thenReturn(List.of(enrollment));

        var result = teacherService.classesForTeacher();

        assertEquals(1, result.size());
        assertEquals("12A1", result.getFirst().name());
        assertEquals("Student", result.getFirst().students().getFirst().fullName());
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

    @Test
    void studentReplayUsesTheAssignmentPinnedRunInsteadOfTheLatestRun() {
        SimulationService replay = mock(SimulationService.class);
        AssignmentService replayService = new AssignmentService(assignments, submissions, null, null,
                currentUser, replay, null, null);
        UUID pinnedRun = UUID.randomUUID();
        Simulation simulation = new Simulation();
        LibraryItem libraryItem = new LibraryItem();
        libraryItem.setSimulation(simulation);
        assignment.setQuestions(json.createObjectNode().put("activityType", "FREE_EXPLORATION"));
        assignment.setLibraryItem(libraryItem);
        assignment.setAssignedSimulationRunId(pinnedRun);

        replayService.simulationForStudent(id);

        verify(replay).replay(simulation, pinnedRun);
        verify(replay, never()).latestFor(any());
        verify(replay, never()).runIdAtOrBefore(any(), any());
    }

    @Test
    void legacyAssignmentResolvesTheRunAtAssignmentTimeWhenNoPinnedRunExists() {
        SimulationService replay = mock(SimulationService.class);
        AssignmentService replayService = new AssignmentService(assignments, submissions, null, null,
                currentUser, replay, null, null);
        Simulation simulation = new Simulation();
        LibraryItem libraryItem = new LibraryItem();
        libraryItem.setSimulation(simulation);
        assignment.setQuestions(json.createObjectNode().put("activityType", "FREE_EXPLORATION"));
        assignment.setLibraryItem(libraryItem);
        Instant assignedAt = Instant.parse("2026-01-01T00:00:00Z");
        assignment.setAssignedAt(assignedAt);
        UUID historicalRun = UUID.randomUUID();
        when(replay.runIdAtOrBefore(simulation, assignedAt)).thenReturn(historicalRun);

        replayService.simulationForStudent(id);

        verify(replay).runIdAtOrBefore(simulation, assignedAt);
        verify(replay).replay(simulation, historicalRun);
    }
}
