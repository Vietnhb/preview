package com.example.backend.service.assignment;

import com.example.backend.service.account.CurrentUserService;
import com.example.backend.service.simulation.SimulationService;

import com.example.backend.dto.assignment.AssignmentResponse;
import com.example.backend.dto.assignment.AssignmentSubmissionResponse;
import com.example.backend.dto.assignment.CreateAssignmentRequest;
import com.example.backend.dto.assignment.SubmitPredictionRequest;
import com.example.backend.dto.simulation.ParameterAdjustmentRequest;
import com.example.backend.dto.simulation.SimulationResponse;
import com.example.backend.entity.assignment.Assignment;
import com.example.backend.entity.assignment.AssignmentSubmission;
import com.example.backend.entity.problem.Specification;
import com.example.backend.entity.library.LibraryItem;
import com.example.backend.entity.enums.SimulationStatus;
import com.example.backend.entity.enums.RoleName;
import com.example.backend.entity.account.User;
import com.example.backend.exception.ApiException;
import com.example.backend.repository.assignment.AssignmentRepository;
import com.example.backend.repository.assignment.AssignmentSubmissionRepository;
import com.example.backend.repository.school.ClassEnrollmentRepository;
import com.example.backend.repository.school.ClassTeacherAssignmentRepository;
import com.example.backend.repository.library.LibraryItemRepository;
import com.example.backend.repository.account.UserRepository;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.math.BigDecimal;
import com.fasterxml.jackson.databind.JsonNode;

@Service
public class AssignmentService {
    private static final String ASSIGNMENT_NOT_FOUND = "Assignment not found";
    private final AssignmentRepository assignmentRepository;
    private final AssignmentSubmissionRepository submissionRepository;
    private final LibraryItemRepository libraryItemRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final SimulationService simulationService;
    private final ClassEnrollmentRepository classEnrollments;
    private final ClassTeacherAssignmentRepository classTeacherAssignments;

    public record AssignmentReport(long assigned, long submitted, long pending, long graded, long confirmed,
                                   long retryAllowed, BigDecimal averageScore, BigDecimal maxScore) { }

    @Autowired
    public AssignmentService(AssignmentRepository assignmentRepository,
                             AssignmentSubmissionRepository submissionRepository,
                             LibraryItemRepository libraryItemRepository,
                             UserRepository userRepository,
                             CurrentUserService currentUserService,
                             SimulationService simulationService,
                             ClassEnrollmentRepository classEnrollments,
                             ClassTeacherAssignmentRepository classTeacherAssignments) {
        this.assignmentRepository = assignmentRepository;
        this.submissionRepository = submissionRepository;
        this.libraryItemRepository = libraryItemRepository;
        this.userRepository = userRepository;
        this.currentUserService = currentUserService;
        this.simulationService = simulationService;
        this.classEnrollments = classEnrollments;
        this.classTeacherAssignments = classTeacherAssignments;
    }

    @Transactional
    public AssignmentResponse create(CreateAssignmentRequest request) {
        User teacher = currentUserService.requireCurrentUser();
        LibraryItem libraryItem = libraryItemRepository.findByIdAndOwnerIdAndActiveTrue(request.libraryItemId(), teacher.getId())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Personal library item not found"));
        Specification specification = libraryItem.getSpecification();
        if (libraryItem.getSimulation() == null || libraryItem.getSimulation().getStatus() != SimulationStatus.READY
                || !"PASSED".equals(specification.getValidationStatus()))
            throw new ApiException(HttpStatus.CONFLICT, "Only saved validated simulations can be assigned");
        for (Integer studentId : request.studentIds()) {
            User student = userRepository.findById(studentId)
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Student not found: " + studentId));
            String role = student.getRole() == null ? "" : student.getRole().getName();
            if (!RoleName.STUDENT.matches(role)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Every assignee must have STUDENT role");
            }
            validateTeacherClassAccess(teacher, student);
        }
        Assignment assignment = new Assignment();
        assignment.setLibraryItem(libraryItem);
        assignment.setSpecification(specification);
        assignment.setTeacher(teacher);
        if (simulationService != null) {
            assignment.setAssignedSimulationRunId(simulationService.latestRunId(libraryItem.getSimulation()));
        }
        assignment.setTitle(request.title().trim());
        assignment.setDescription(request.description());
        assignment.setQuestions(request.questions());
        assignment.setGradingCriteria(request.gradingCriteria());
        assignment.setMaxScore(request.maxScore() == null || request.maxScore().signum() <= 0 ? BigDecimal.TEN : request.maxScore());
        assignment.setAutoGrade(Boolean.TRUE.equals(request.autoGrade()));
        assignment.setAssignedStudentIds(request.studentIds());
        assignment.setDueAt(request.dueAt());
        assignment.setAssignedAt(Instant.now());
        return toResponse(assignmentRepository.save(assignment));
    }

    private void validateTeacherClassAccess(User teacher, User student) {
        if (classEnrollments == null || classTeacherAssignments == null)
            throw new IllegalStateException("Class authorization repositories are not configured");
        if (teacher.getRole() != null && RoleName.ADMIN.matches(teacher.getRole().getName())) return;
        if (teacher.getSchool() == null || student.getSchool() == null
                || !teacher.getSchool().getId().equals(student.getSchool().getId()))
            throw new ApiException(HttpStatus.FORBIDDEN, "Teacher and student must belong to the same school");
        boolean assigned = classEnrollments.findActiveEnrollmentsByStudentId(student.getId()).stream()
                .filter(enrollment -> enrollment.getSchoolClass() != null
                        && enrollment.getSchoolClass().getSchool() != null
                        && teacher.getSchool().getId().equals(enrollment.getSchoolClass().getSchool().getId()))
                .anyMatch(enrollment -> classTeacherAssignments.existsBySchoolClassIdAndTeacherIdAndIsActiveTrue(
                        enrollment.getSchoolClass().getId(), teacher.getId()));
        if (!assigned) throw new ApiException(HttpStatus.FORBIDDEN, "Teacher is not assigned to the student's class");
    }

    @Transactional(readOnly = true)
    public List<AssignmentResponse> forTeacher() {
        User teacher = currentUserService.requireCurrentUser();
        return assignmentRepository.findByTeacherIdOrderByCreatedAtDesc(teacher.getId()).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AssignmentResponse> forStudent() {
        User student = currentUserService.requireCurrentUser();
        return assignmentRepository.findByAssignedStudentIdsContaining(student.getId()).stream()
                .filter(item -> item.getStatus() == com.example.backend.entity.enums.AssignmentStatus.ACTIVE)
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true, noRollbackFor = Exception.class)
    public SimulationResponse simulationForStudent(java.util.UUID assignmentId) {
        User student = currentUserService.requireCurrentUser();
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ASSIGNMENT_NOT_FOUND));
        if (!assignment.getAssignedStudentIds().contains(student.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Assignment is not assigned to this student");
        }
        if (!submissionRepository.existsByAssignmentIdAndStudentId(assignmentId, student.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Submit a prediction before viewing the simulation");
        }
        if (assignment.getLibraryItem() == null || assignment.getLibraryItem().getSimulation() == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Assigned simulation not found");
        }
        java.util.UUID runId = assignment.getAssignedSimulationRunId();
        if (runId == null) {
            // Legacy assignments predate the run snapshot column. Select the last
            // run available when the assignment was created instead of a later
            // teacher adjustment, preserving the best reproducible result.
            runId = simulationService.runIdAtOrBefore(
                    assignment.getLibraryItem().getSimulation(), assignment.getAssignedAt());
        }
        return simulationService.replay(assignment.getLibraryItem().getSimulation(), runId);
    }

    @Transactional(readOnly = true, noRollbackFor = Exception.class)
    public SimulationResponse adjustSimulationForStudent(java.util.UUID assignmentId,
                                                          ParameterAdjustmentRequest request) {
        User student = currentUserService.requireCurrentUser();
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ASSIGNMENT_NOT_FOUND));
        if (!assignment.getAssignedStudentIds().contains(student.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Assignment is not assigned to this student");
        }
        if (!submissionRepository.existsByAssignmentIdAndStudentId(assignmentId, student.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Submit a prediction before adjusting the simulation");
        }
        if (assignment.getLibraryItem() == null || assignment.getLibraryItem().getSimulation() == null) {
            throw new ApiException(HttpStatus.NOT_FOUND, "Assigned simulation not found");
        }

        var simulation = assignment.getLibraryItem().getSimulation();
        if (request.simulationId() == null || !simulation.getId().equals(request.simulationId())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Simulation does not belong to this assignment");
        }
        java.util.UUID runId = assignment.getAssignedSimulationRunId();
        if (runId == null) {
            runId = simulationService.runIdAtOrBefore(simulation, assignment.getAssignedAt());
        }
        return simulationService.previewAdjustment(simulation, runId, request.adjustableParams());
    }

    @Transactional
    public AssignmentSubmissionResponse submit(java.util.UUID assignmentId, SubmitPredictionRequest request) {
        User student = currentUserService.requireCurrentUser();
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ASSIGNMENT_NOT_FOUND));
        if (!assignment.getAssignedStudentIds().contains(student.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Assignment is not assigned to this student");
        }
        AssignmentSubmission submission = submissionRepository.findByAssignmentIdAndStudentId(assignmentId, student.getId()).orElse(null);
        if (submission != null && !submission.isRetryAllowed())
            throw new ApiException(HttpStatus.CONFLICT, "Prediction already submitted");
        if (submission == null) submission = new AssignmentSubmission();
        submission.setAssignment(assignment);
        submission.setStudent(student);
        submission.setPredictions(request.predictions());
        submission.setSubmittedAt(Instant.now());
        submission.setRetryAllowed(false);
        submission.setGradingStatus(com.example.backend.entity.enums.GradingStatus.PENDING);
        submission.setScore(null); submission.setMaxScore(null); submission.setFeedback(null); submission.setGradedAt(null); submission.setGradedBy(null);
        if (assignment.isAutoGrade()) autoGrade(assignment, submission);
        return toSubmission(submissionRepository.save(submission));
    }

    private void autoGrade(Assignment assignment, AssignmentSubmission submission) {
        JsonNode criteria = assignment.getGradingCriteria();
        JsonNode prediction = submission.getPredictions();
        if (criteria == null || prediction == null || !criteria.has("expectedValue") || !prediction.has("estimatedValue")) return;
        double expected = criteria.get("expectedValue").asDouble();
        double actual = prediction.get("estimatedValue").asDouble();
        double tolerance = criteria.has("tolerance") ? Math.max(0d, criteria.get("tolerance").asDouble()) : 0d;
        submission.setMaxScore(assignment.getMaxScore());
        submission.setScore(Math.abs(expected - actual) <= tolerance ? assignment.getMaxScore() : BigDecimal.ZERO);
        submission.setGradingStatus(com.example.backend.entity.enums.GradingStatus.AI_GRADED);
        submission.setGradedAt(Instant.now()); submission.setRetryAllowed(false);
    }

    @Transactional
    public AssignmentSubmissionResponse grade(java.util.UUID assignmentId, java.util.UUID submissionId,
                                               BigDecimal score, BigDecimal maxScore, String feedback, boolean confirm) {
        User teacher = currentUserService.requireCurrentUser();
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ASSIGNMENT_NOT_FOUND));
        if (!RoleName.ADMIN.matches(teacher.getRole() == null ? null : teacher.getRole().getName())
                && !assignment.getTeacher().getId().equals(teacher.getId()))
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the assignment teacher can grade submissions");
        AssignmentSubmission submission = submissionRepository.findById(submissionId)
                .filter(item -> item.getAssignment().getId().equals(assignmentId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Submission not found"));
        if (score.compareTo(maxScore) > 0) throw new ApiException(HttpStatus.BAD_REQUEST, "Score cannot exceed max score");
        submission.setScore(score); submission.setMaxScore(maxScore); submission.setFeedback(feedback == null ? null : feedback.trim());
        submission.setGradingStatus(confirm ? com.example.backend.entity.enums.GradingStatus.TEACHER_CONFIRMED : com.example.backend.entity.enums.GradingStatus.AI_GRADED);
        submission.setGradedAt(Instant.now()); submission.setGradedBy(teacher); submission.setRetryAllowed(false);
        return toSubmission(submissionRepository.save(submission));
    }

    @Transactional
    public AssignmentSubmissionResponse reopen(java.util.UUID assignmentId, java.util.UUID submissionId) {
        User teacher = currentUserService.requireCurrentUser();
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ASSIGNMENT_NOT_FOUND));
        if (!RoleName.ADMIN.matches(teacher.getRole() == null ? null : teacher.getRole().getName())
                && !assignment.getTeacher().getId().equals(teacher.getId()))
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the assignment teacher can reopen submissions");
        AssignmentSubmission submission = submissionRepository.findById(submissionId)
                .filter(item -> item.getAssignment().getId().equals(assignmentId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Submission not found"));
        submission.setRetryAllowed(true); submission.setGradingStatus(com.example.backend.entity.enums.GradingStatus.RETURNED);
        return toSubmission(submissionRepository.save(submission));
    }

    @Transactional(readOnly = true)
    public List<AssignmentSubmissionResponse> submissions(java.util.UUID assignmentId) {
        User teacher = currentUserService.requireCurrentUser();
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ASSIGNMENT_NOT_FOUND));
        if (!assignment.getTeacher().getId().equals(teacher.getId())
                && !RoleName.ADMIN.matches(teacher.getRole() == null ? null : teacher.getRole().getName())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the teacher can view submissions");
        }
        return submissionRepository.findByAssignmentIdOrderBySubmittedAtDesc(assignmentId).stream().map(this::toSubmission).toList();
    }

    @Transactional(readOnly = true)
    public AssignmentReport report(java.util.UUID assignmentId) {
        User teacher = currentUserService.requireCurrentUser();
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ASSIGNMENT_NOT_FOUND));
        boolean admin = teacher.getRole() != null && RoleName.ADMIN.matches(teacher.getRole().getName());
        if (!admin && !assignment.getTeacher().getId().equals(teacher.getId()))
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the assignment teacher can view reports");
        List<AssignmentSubmission> rows = submissionRepository.findByAssignmentIdOrderBySubmittedAtDesc(assignmentId);
        BigDecimal total = rows.stream().map(AssignmentSubmission::getScore).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        long graded = rows.stream().filter(row -> row.getGradingStatus() == com.example.backend.entity.enums.GradingStatus.AI_GRADED || row.getGradingStatus() == com.example.backend.entity.enums.GradingStatus.TEACHER_CONFIRMED).count();
        long confirmed = rows.stream().filter(row -> row.getGradingStatus() == com.example.backend.entity.enums.GradingStatus.TEACHER_CONFIRMED).count();
        return new AssignmentReport(assignment.getAssignedStudentIds().size(), rows.size(), rows.stream().filter(row -> row.getGradingStatus() == com.example.backend.entity.enums.GradingStatus.PENDING).count(), graded, confirmed, rows.stream().filter(AssignmentSubmission::isRetryAllowed).count(), graded == 0 ? null : total.divide(BigDecimal.valueOf(graded), 3, java.math.RoundingMode.HALF_UP), assignment.getMaxScore());
    }

    private AssignmentResponse toResponse(Assignment item) {
        User current = currentUserService.requireCurrentUser();
        boolean predictionSubmitted = current.getRole() != null
                && RoleName.STUDENT.matches(current.getRole().getName())
                && submissionRepository.existsByAssignmentIdAndStudentId(item.getId(), current.getId());
        AssignmentSubmission ownSubmission = current.getRole() != null && RoleName.STUDENT.matches(current.getRole().getName())
                ? submissionRepository.findByAssignmentIdAndStudentId(item.getId(), current.getId()).orElse(null) : null;
        return new AssignmentResponse(item.getId(), item.getLibraryItem() == null ? null : item.getLibraryItem().getId(),
                item.getSpecification().getId(), item.getAssignedSimulationRunId(), item.getTitle(), item.getDescription(),
                item.getQuestions(), item.getAssignedStudentIds() == null ? Set.of() : Set.copyOf(item.getAssignedStudentIds()),
                item.getStatus(), item.getAssignedAt(), item.getDueAt(),
                predictionSubmitted, item.getGradingCriteria(), item.getMaxScore(), item.isAutoGrade(),
                ownSubmission == null ? null : ownSubmission.getScore(), ownSubmission == null ? null : ownSubmission.getFeedback(),
                ownSubmission == null ? null : ownSubmission.getGradingStatus(), ownSubmission != null && ownSubmission.isRetryAllowed());
    }

    private AssignmentSubmissionResponse toSubmission(AssignmentSubmission item) {
        return new AssignmentSubmissionResponse(item.getId(), item.getAssignment().getId(), item.getStudent().getId(),
                item.getStudent().getFullName(), item.getPredictions(), item.getSubmittedAt(), item.getScore(), item.getMaxScore(),
                item.getFeedback(), item.getGradingStatus(), item.getGradedAt(), item.isRetryAllowed());
    }
}
