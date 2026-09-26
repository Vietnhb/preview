package com.example.backend.service.assignment;

import com.example.backend.service.account.CurrentUserService;
import com.example.backend.service.simulation.SimulationService;

import com.example.backend.dto.assignment.AssignmentResponse;
import com.example.backend.dto.assignment.AssignmentSubmissionResponse;
import com.example.backend.dto.assignment.CreateAssignmentRequest;
import com.example.backend.dto.assignment.SubmitPredictionRequest;
import com.example.backend.dto.assignment.CompleteAssignmentRequest;
import com.example.backend.dto.simulation.ParameterAdjustmentRequest;
import com.example.backend.dto.simulation.SimulationResponse;
import com.example.backend.entity.assignment.Assignment;
import com.example.backend.entity.assignment.AssignmentSubmission;
import com.example.backend.entity.problem.Specification;
import com.example.backend.entity.library.LibraryItem;
import com.example.backend.entity.enums.SimulationStatus;
import com.example.backend.entity.enums.RoleName;
import com.example.backend.entity.account.User;
import com.example.backend.entity.school.SchoolClass;
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
import java.util.UUID;
import java.math.BigDecimal;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;

@Service
public class AssignmentService {
    private static final String MEASUREMENT = "MEASUREMENT";
    private static final String EXPECTED_VALUE = "expectedValue";
    private static final String TOLERANCE = "tolerance";
    private static final String ANSWER_TEXT = "answerText";
    private static final String ESTIMATED_VALUE = "estimatedValue";
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
    public record TeacherClassStudent(Integer id, String fullName) { }
    public record TeacherClassOption(UUID id, String name, Integer gradeLevel, String schoolYear,
                                     String subject, List<TeacherClassStudent> students) { }

    private static String activityType(JsonNode questions) {
        String value = questions == null ? "" : questions.path("activityType").asText("");
        return switch (value) {
            case MEASUREMENT, "PARAMETER_INVESTIGATION", "FREE_EXPLORATION" -> value;
            default -> "PREDICT_OBSERVE_EXPLAIN";
        };
    }

    private static boolean requiresPrediction(Assignment assignment) {
        return "PREDICT_OBSERVE_EXPLAIN".equals(activityType(assignment.getQuestions()));
    }

    private static double sampleSeries(SimulationResponse simulation, String source, double sampleTime) {
        String[] path = source.split("\\.", 2);
        if (path.length != 2) throw new ApiException(HttpStatus.BAD_REQUEST, "Invalid simulation series source");
        java.util.Map<String, java.util.List<Double>> group = switch (path[0]) {
            case "positions" -> simulation.positions();
            case "velocities" -> simulation.velocities();
            case "accelerations" -> simulation.accelerations();
            case "values" -> simulation.values();
            default -> null;
        };
        java.util.List<Double> values = group == null ? null : group.get(path[1]);
        java.util.List<Double> times = simulation.time();
        if (values == null || values.isEmpty() || times == null || times.size() != values.size()
                || sampleTime < times.getFirst() || sampleTime > times.getLast())
            throw new ApiException(HttpStatus.BAD_REQUEST, "Measurement target is not available at the selected time");
        if (sampleTime <= times.getFirst()) return values.getFirst();
        for (int i = 1; i < times.size(); i++) {
            if (times.get(i) >= sampleTime) {
                double t0 = times.get(i - 1);
                double t1 = times.get(i);
                double v0 = values.get(i - 1);
                double v1 = values.get(i);
                return t1 == t0 ? v1 : v0 + (sampleTime - t0) / (t1 - t0) * (v1 - v0);
            }
        }
        return values.getLast();
    }

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
        if (request.questions() == null || !request.questions().path("prompt").isTextual()
                || request.questions().path("prompt").asText().isBlank())
            throw new ApiException(HttpStatus.BAD_REQUEST, "Assignment question is required");
        if (request.dueAt() != null && !request.dueAt().isAfter(Instant.now()))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Due date must be in the future");
        if (request.maxScore() != null && request.maxScore().signum() <= 0)
            throw new ApiException(HttpStatus.BAD_REQUEST, "Maximum score must be positive");
        if (RoleName.TEACHER.matches(teacher.getRole() == null ? null : teacher.getRole().getName())
                && request.classId() == null)
            throw new ApiException(HttpStatus.BAD_REQUEST, "Select a class before assigning this activity");
        String activityType = activityType(request.questions());
        if (Boolean.TRUE.equals(request.autoGrade()) && !MEASUREMENT.equals(activityType)) {
            JsonNode criteria = request.gradingCriteria();
            if (criteria == null || !finiteNumber(criteria.path(EXPECTED_VALUE))
                    || !finiteNumber(criteria.path(TOLERANCE)) || criteria.path(TOLERANCE).asDouble() < 0)
                throw new ApiException(HttpStatus.BAD_REQUEST, "A numeric answer and non-negative tolerance are required");
        }
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
        SchoolClass targetClass = null;
        if (request.classId() != null) {
            if (classTeacherAssignments == null || classEnrollments == null
                    || (!RoleName.ADMIN.matches(teacher.getRole() == null ? null : teacher.getRole().getName())
                    && !classTeacherAssignments.existsBySchoolClassIdAndTeacherIdAndIsActiveTrue(request.classId(), teacher.getId())))
                throw new ApiException(HttpStatus.FORBIDDEN, "Teacher is not assigned to the selected class");
            targetClass = classTeacherAssignments.findByClassIdAndIsActiveTrue(request.classId()).stream()
                    .map(item -> item.getSchoolClass()).findFirst()
                    .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Selected class is not active"));
            Set<Integer> classStudentIds = classEnrollments.findActiveStudentsByClassId(request.classId()).stream()
                    .map(item -> item.getStudent().getId()).collect(java.util.stream.Collectors.toSet());
            if (!classStudentIds.containsAll(request.studentIds()))
                throw new ApiException(HttpStatus.BAD_REQUEST, "Every selected student must belong to the selected class");
        }
        Assignment assignment = new Assignment();
        assignment.setLibraryItem(libraryItem);
        assignment.setSpecification(specification);
        assignment.setTeacher(teacher);
        assignment.setSchoolClass(targetClass);
        UUID assignedRunId = simulationService == null ? null : simulationService.latestRunId(libraryItem.getSimulation());
        assignment.setAssignedSimulationRunId(assignedRunId);
        assignment.setTitle(request.title().trim());
        assignment.setDescription(request.description());
        assignment.setQuestions(request.questions());
        JsonNode gradingCriteria = request.gradingCriteria();
        boolean autoGrade = Boolean.TRUE.equals(request.autoGrade());
        if (MEASUREMENT.equals(activityType)) {
            JsonNode measurement = request.questions().path("measurement");
            String source = measurement.path("seriesSource").asText("");
            double sampleTime = measurement.path("sampleTime").asDouble(Double.NaN);
            double tolerance = measurement.path(TOLERANCE).asDouble(Double.NaN);
            if (source.isBlank() || !Double.isFinite(sampleTime) || !Double.isFinite(tolerance) || tolerance < 0)
                throw new ApiException(HttpStatus.BAD_REQUEST, "Measurement assignments require a series, sample time and non-negative tolerance");
            SimulationResponse snapshot = simulationService.replay(libraryItem.getSimulation(), assignedRunId);
            double expected = sampleSeries(snapshot, source, sampleTime);
            ObjectNode derived = JsonNodeFactory.instance.objectNode();
            derived.put(EXPECTED_VALUE, expected);
            derived.put(TOLERANCE, tolerance);
            derived.put("seriesSource", source);
            derived.put("sampleTime", sampleTime);
            derived.put("unit", measurement.path("unit").asText(""));
            gradingCriteria = derived;
            autoGrade = true;
        } else if ("PARAMETER_INVESTIGATION".equals(activityType)) {
            String parameterKey = request.questions().path("investigation").path("parameterKey").asText("");
            SimulationResponse snapshot = simulationService.replay(libraryItem.getSimulation(), assignedRunId);
            if (parameterKey.isBlank() || snapshot.adjustableParams() == null || !snapshot.adjustableParams().containsKey(parameterKey))
                throw new ApiException(HttpStatus.BAD_REQUEST, "Investigation parameter is not adjustable in this simulation");
            autoGrade = false;
            gradingCriteria = null;
        }
        assignment.setGradingCriteria(gradingCriteria);
        assignment.setMaxScore(request.maxScore() == null || request.maxScore().signum() <= 0 ? BigDecimal.TEN : request.maxScore());
        assignment.setAutoGrade(autoGrade);
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
    public List<TeacherClassOption> classesForTeacher() {
        User teacher = currentUserService.requireCurrentUser();
        if (classTeacherAssignments == null || classEnrollments == null) return List.of();
        return classTeacherAssignments.findActiveByTeacherId(teacher.getId()).stream()
                .map(item -> item.getSchoolClass())
                .distinct()
                .map(schoolClass -> new TeacherClassOption(schoolClass.getId(), schoolClass.getName(),
                        schoolClass.getGradeLevel(), schoolClass.getSchoolYear(), schoolClass.getSubject(),
                        classEnrollments.findActiveStudentsByClassId(schoolClass.getId()).stream()
                                .map(enrollment -> new TeacherClassStudent(enrollment.getStudent().getId(), enrollment.getStudent().getFullName()))
                                .toList()))
                .toList();
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
        if (requiresPrediction(assignment) && !submissionRepository.existsByAssignmentIdAndStudentId(assignmentId, student.getId())) {
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
        if (requiresPrediction(assignment) && !submissionRepository.existsByAssignmentIdAndStudentId(assignmentId, student.getId())) {
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
        if (assignment.getStatus() != com.example.backend.entity.enums.AssignmentStatus.ACTIVE)
            throw new ApiException(HttpStatus.BAD_REQUEST, "Assignment is closed");
        // Late work remains accepted and is identifiable from dueAt/submittedAt.
        JsonNode prediction = request.predictions();
        if (prediction == null || !prediction.path(ANSWER_TEXT).isTextual()
                || prediction.path(ANSWER_TEXT).asText().isBlank())
            throw new ApiException(HttpStatus.BAD_REQUEST, "Prediction answer is required");
        if (assignment.isAutoGrade() && !finiteNumber(prediction.path(ESTIMATED_VALUE)))
            throw new ApiException(HttpStatus.BAD_REQUEST, "A numeric prediction is required for this assignment");
        if (submission != null && !submission.isRetryAllowed())
            throw new ApiException(HttpStatus.CONFLICT, "Prediction already submitted");
        if (submission == null) submission = new AssignmentSubmission();
        submission.setAssignment(assignment);
        submission.setStudent(student);
        submission.setPredictions(request.predictions());
        submission.setSubmittedAt(Instant.now());
        submission.setCompletedAt(null);
        submission.setRetryAllowed(false);
        submission.setGradingStatus(com.example.backend.entity.enums.GradingStatus.PENDING);
        submission.setScore(null); submission.setMaxScore(null); submission.setFeedback(null); submission.setGradedAt(null); submission.setGradedBy(null);
        return toSubmission(submissionRepository.save(submission));
    }

    @Transactional
    public AssignmentSubmissionResponse complete(java.util.UUID assignmentId, CompleteAssignmentRequest request) {
        User student = currentUserService.requireCurrentUser();
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ASSIGNMENT_NOT_FOUND));
        if (!assignment.getAssignedStudentIds().contains(student.getId()))
            throw new ApiException(HttpStatus.FORBIDDEN, "Assignment is not assigned to this student");
        if (assignment.getStatus() != com.example.backend.entity.enums.AssignmentStatus.ACTIVE)
            throw new ApiException(HttpStatus.BAD_REQUEST, "Assignment is closed");
        AssignmentSubmission submission = submissionRepository.findByAssignmentIdAndStudentId(assignmentId, student.getId()).orElse(null);
        if (submission == null && requiresPrediction(assignment))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Submit a prediction before completing the assignment");
        if (submission == null) {
            submission = new AssignmentSubmission();
            submission.setAssignment(assignment);
            submission.setStudent(student);
            submission.setSubmittedAt(Instant.now());
            submission.setPredictions(JsonNodeFactory.instance.objectNode());
        }
        if (submission.getCompletedAt() != null && !submission.isRetryAllowed())
            throw new ApiException(HttpStatus.CONFLICT, "Assignment already submitted");
        if (!(submission.getPredictions() instanceof ObjectNode prediction))
            throw new ApiException(HttpStatus.BAD_REQUEST, "Prediction answer is invalid");
        String answer = request.answerText() == null || request.answerText().isBlank()
                ? request.conclusion().trim() : request.answerText().trim();
            prediction.put(ANSWER_TEXT, answer);
        prediction.put("conclusion", request.conclusion().trim());
        if (request.estimatedValue() != null && Double.isFinite(request.estimatedValue()))
            prediction.put(ESTIMATED_VALUE, request.estimatedValue());
        if (assignment.isAutoGrade() && !finiteNumber(prediction.path(ESTIMATED_VALUE)))
            throw new ApiException(HttpStatus.BAD_REQUEST, "A numeric measurement is required for this assignment");
        submission.setCompletedAt(Instant.now());
        submission.setRetryAllowed(false);
        submission.setGradingStatus(com.example.backend.entity.enums.GradingStatus.PENDING);
        if (assignment.isAutoGrade()) autoGrade(assignment, submission);
        return toSubmission(submissionRepository.save(submission));
    }

    private static boolean finiteNumber(JsonNode value) {
        return value.isNumber() && Double.isFinite(value.asDouble());
    }

    private void autoGrade(Assignment assignment, AssignmentSubmission submission) {
        JsonNode criteria = assignment.getGradingCriteria();
        JsonNode prediction = submission.getPredictions();
        if (criteria == null || prediction == null || !finiteNumber(criteria.path(EXPECTED_VALUE))
                || !finiteNumber(prediction.path(ESTIMATED_VALUE))) return;
        double expected = criteria.get(EXPECTED_VALUE).asDouble();
        double actual = prediction.get(ESTIMATED_VALUE).asDouble();
        double tolerance = criteria.has(TOLERANCE) ? Math.max(0d, criteria.get(TOLERANCE).asDouble()) : 0d;
        submission.setMaxScore(assignment.getMaxScore());
        submission.setScore(Math.abs(expected - actual) <= tolerance ? assignment.getMaxScore() : BigDecimal.ZERO);
        submission.setGradingStatus(com.example.backend.entity.enums.GradingStatus.AI_GRADED);
        submission.setGradedAt(Instant.now()); submission.setRetryAllowed(false);
    }

    @Transactional
    public AssignmentSubmissionResponse grade(java.util.UUID assignmentId, java.util.UUID submissionId,
                                               BigDecimal score, String feedback, boolean confirm) {
        User teacher = currentUserService.requireCurrentUser();
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, ASSIGNMENT_NOT_FOUND));
        if (!RoleName.ADMIN.matches(teacher.getRole() == null ? null : teacher.getRole().getName())
                && !assignment.getTeacher().getId().equals(teacher.getId()))
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the assignment teacher can grade submissions");
        AssignmentSubmission submission = submissionRepository.findById(submissionId)
                .filter(item -> item.getAssignment().getId().equals(assignmentId))
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Submission not found"));
        if (submission.getCompletedAt() == null)
            throw new ApiException(HttpStatus.BAD_REQUEST, "The student has not submitted this assignment yet");
        BigDecimal assignmentMaxScore = assignment.getMaxScore();
        if (score.compareTo(assignmentMaxScore) > 0) throw new ApiException(HttpStatus.BAD_REQUEST, "Score cannot exceed assignment max score");
        submission.setScore(score); submission.setMaxScore(assignmentMaxScore); submission.setFeedback(feedback == null ? null : feedback.trim());
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
        submission.setCompletedAt(null);
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
        List<AssignmentSubmission> completed = rows.stream().filter(row -> row.getCompletedAt() != null).toList();
        BigDecimal total = completed.stream().map(AssignmentSubmission::getScore).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        long graded = completed.stream().filter(row -> row.getGradingStatus() == com.example.backend.entity.enums.GradingStatus.AI_GRADED || row.getGradingStatus() == com.example.backend.entity.enums.GradingStatus.TEACHER_CONFIRMED).count();
        long confirmed = completed.stream().filter(row -> row.getGradingStatus() == com.example.backend.entity.enums.GradingStatus.TEACHER_CONFIRMED).count();
        return new AssignmentReport(assignment.getAssignedStudentIds().size(), completed.size(),
                (long) assignment.getAssignedStudentIds().size() - completed.size(), graded, confirmed,
                rows.stream().filter(AssignmentSubmission::isRetryAllowed).count(),
                graded == 0 ? null : total.divide(BigDecimal.valueOf(graded), 3, java.math.RoundingMode.HALF_UP),
                assignment.getMaxScore());
    }

    private AssignmentResponse toResponse(Assignment item) {
        User current = currentUserService.requireCurrentUser();
        boolean predictionSubmitted = current.getRole() != null
                && RoleName.STUDENT.matches(current.getRole().getName())
                && submissionRepository.existsByAssignmentIdAndStudentId(item.getId(), current.getId());
        AssignmentSubmission ownSubmission = current.getRole() != null && RoleName.STUDENT.matches(current.getRole().getName())
                ? submissionRepository.findByAssignmentIdAndStudentId(item.getId(), current.getId()).orElse(null) : null;
        return new AssignmentResponse(item.getId(), item.getLibraryItem() == null ? null : item.getLibraryItem().getId(),
                item.getLibraryItem() == null ? null : item.getLibraryItem().getTitle(),
                item.getSchoolClass() == null ? null : item.getSchoolClass().getId(),
                item.getSchoolClass() == null ? null : item.getSchoolClass().getName(),
                item.getSchoolClass() == null ? null : item.getSchoolClass().getGradeLevel(),
                item.getSpecification().getId(), item.getAssignedSimulationRunId(), item.getTitle(), item.getDescription(),
                item.getQuestions(), item.getAssignedStudentIds() == null ? Set.of() : Set.copyOf(item.getAssignedStudentIds()),
                item.getStatus(), item.getAssignedAt(), item.getDueAt(),
                predictionSubmitted, ownSubmission != null && ownSubmission.getCompletedAt() != null,
                ownSubmission == null ? null : ownSubmission.getCompletedAt(),
                current.getRole() != null && RoleName.STUDENT.matches(current.getRole().getName())
                        ? null : item.getGradingCriteria(), item.getMaxScore(), item.isAutoGrade(),
                ownSubmission == null ? null : ownSubmission.getScore(), ownSubmission == null ? null : ownSubmission.getFeedback(),
                ownSubmission == null ? null : ownSubmission.getGradingStatus(), ownSubmission != null && ownSubmission.isRetryAllowed(),
                ownSubmission == null ? null : ownSubmission.getPredictions());
    }

    private AssignmentSubmissionResponse toSubmission(AssignmentSubmission item) {
        return new AssignmentSubmissionResponse(item.getId(), item.getAssignment().getId(), item.getStudent().getId(),
                item.getStudent().getFullName(), item.getPredictions(), item.getSubmittedAt(), item.getCompletedAt(), item.getScore(), item.getMaxScore(),
                item.getFeedback(), item.getGradingStatus(), item.getGradedAt(), item.isRetryAllowed());
    }
}
