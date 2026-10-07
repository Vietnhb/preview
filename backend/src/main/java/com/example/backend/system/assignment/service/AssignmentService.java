package com.example.backend.system.assignment.service;

import com.example.backend.exception.ApiException;
import com.example.backend.system.account.model.entity.User;
import com.example.backend.system.account.model.enums.RoleName;
import com.example.backend.system.account.repository.UserRepository;
import com.example.backend.system.account.service.CurrentUserService;
import com.example.backend.system.assignment.dto.AssignmentContracts.AssignmentReport;
import com.example.backend.system.assignment.dto.AssignmentContracts.AssignmentResponse;
import com.example.backend.system.assignment.dto.AssignmentContracts.AssignmentSubmissionResponse;
import com.example.backend.system.assignment.dto.AssignmentContracts.CompleteAssignmentRequest;
import com.example.backend.system.assignment.dto.AssignmentContracts.CreateAssignmentRequest;
import com.example.backend.system.assignment.dto.AssignmentContracts.SubmitPredictionRequest;
import com.example.backend.system.assignment.dto.AssignmentContracts.TeacherClassOption;
import com.example.backend.system.assignment.dto.AssignmentContracts.TeacherClassStudent;
import com.example.backend.system.assignment.dto.AssignmentContracts;
import com.example.backend.system.assignment.model.entity.Assignment;
import com.example.backend.system.assignment.model.entity.AssignmentSubmission;
import com.example.backend.system.assignment.model.enums.AssignmentStatus;
import com.example.backend.system.assignment.model.enums.GradingStatus;
import com.example.backend.system.assignment.repository.AssignmentRepository;
import com.example.backend.system.assignment.repository.AssignmentSubmissionRepository;
import com.example.backend.system.library.model.entity.LibraryItem;
import com.example.backend.system.library.repository.LibraryItemRepository;
import com.example.backend.system.problem.model.entity.Specification;
import com.example.backend.system.school.model.entity.SchoolClass;
import com.example.backend.system.school.repository.ClassEnrollmentRepository;
import com.example.backend.system.school.repository.ClassTeacherAssignmentRepository;
import com.example.backend.system.simulation.dto.SimulationRequests;
import com.example.backend.system.simulation.dto.SimulationResponse;
import com.example.backend.system.simulation.model.entity.Simulation;
import com.example.backend.system.simulation.model.enums.SimulationStatus;
import com.example.backend.system.simulation.service.SimulationService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AssignmentService {
    private static final String MEASUREMENT = "MEASUREMENT";
    private static final String EXPECTED_VALUE = "expectedValue";
    private static final String TOLERANCE = "tolerance";
    private static final String ANSWER_TEXT = "answerText";
    private static final String ESTIMATED_VALUE = "estimatedValue";
    private static final String ASSIGNMENT_NOT_FOUND = "Không tìm thấy bài tập";
    private final AssignmentRepository assignmentRepository;
    private final AssignmentSubmissionRepository submissionRepository;
    private final LibraryItemRepository libraryItemRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;
    private final SimulationService simulationService;
    private final ClassEnrollmentRepository classEnrollments;
    private final ClassTeacherAssignmentRepository classTeacherAssignments;

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
        if (path.length != 2) throw ApiException.badRequest("Nguồn chuỗi dữ liệu mô phỏng không hợp lệ");
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
            throw ApiException.badRequest("Không có dữ liệu đo tại thời điểm đã chọn");
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

    @Transactional
    public AssignmentResponse create(CreateAssignmentRequest request) {
        User teacher = currentUserService.requireCurrentUser();
        if (request.questions() == null || !request.questions().path("prompt").isTextual()
                || request.questions().path("prompt").asText().isBlank())
            throw ApiException.badRequest("Vui lòng nhập câu hỏi bài tập");
        if (request.dueAt() != null && !request.dueAt().isAfter(Instant.now()))
            throw ApiException.badRequest("Hạn nộp phải sau thời điểm hiện tại");
        if (request.maxScore() != null && request.maxScore().signum() <= 0)
            throw ApiException.badRequest("Điểm tối đa phải lớn hơn 0");
        if (RoleName.STAFF.matches(teacher.getRole() == null ? null : teacher.getRole().getName())
                && request.classId() == null)
            throw ApiException.badRequest("Vui lòng chọn lớp trước khi giao bài");
        String activityType = activityType(request.questions());
        if (Boolean.TRUE.equals(request.autoGrade()) && !MEASUREMENT.equals(activityType)) {
            JsonNode criteria = request.gradingCriteria();
            if (criteria == null || !finiteNumber(criteria.path(EXPECTED_VALUE))
                    || !finiteNumber(criteria.path(TOLERANCE)) || criteria.path(TOLERANCE).asDouble() < 0)
                throw ApiException.badRequest("Vui lòng nhập đáp án dạng số và sai số cho phép không âm");
        }
        LibraryItem libraryItem = libraryItemRepository.findByIdAndOwnerIdAndActiveTrue(request.libraryItemId(), teacher.getId())
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy mục trong thư viện cá nhân"));
        Specification specification = libraryItem.getSpecification();
        if (libraryItem.getSimulation() == null || libraryItem.getSimulation().getStatus() != SimulationStatus.READY
                || !"PASSED".equals(specification.getValidationStatus()))
            throw ApiException.conflict("Chỉ có thể giao mô phỏng đã lưu và đã kiểm định");
        for (Integer studentId : request.studentIds()) {
            User student = userRepository.findById(studentId)
                    .orElseThrow(() -> ApiException.badRequest("Không tìm thấy học sinh: " + studentId));
            String role = student.getRole() == null ? "" : student.getRole().getName();
            if (!RoleName.STUDENT.matches(role)) {
                throw ApiException.badRequest("Tất cả người được giao bài phải có vai trò học sinh");
            }
            validateTeacherClassAccess(teacher, student);
        }
        SchoolClass targetClass = null;
        if (request.classId() != null) {
            if (classTeacherAssignments == null || classEnrollments == null
                    || (!RoleName.MANAGER.matches(teacher.getRole() == null ? null : teacher.getRole().getName())
                    && !classTeacherAssignments.existsBySchoolClassIdAndTeacherIdAndIsActiveTrue(request.classId(), teacher.getId())))
                throw ApiException.forbidden("Giáo viên chưa được phân công vào lớp đã chọn");
            targetClass = classTeacherAssignments.findByClassIdAndIsActiveTrue(request.classId()).stream()
                    .map(item -> item.getSchoolClass()).findFirst()
                    .orElseThrow(() -> ApiException.badRequest("Lớp đã chọn không còn hoạt động"));
            Set<Integer> classStudentIds = classEnrollments.findActiveStudentsByClassId(request.classId()).stream()
                    .map(item -> item.getStudent().getId()).collect(java.util.stream.Collectors.toSet());
            if (!classStudentIds.containsAll(request.studentIds()))
                throw ApiException.badRequest("Tất cả học sinh được chọn phải thuộc lớp đã chọn");
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
                throw ApiException.badRequest("Bài tập đo lường cần có chuỗi dữ liệu, thời điểm đo và sai số cho phép không âm");
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
                throw ApiException.badRequest("Không thể điều chỉnh tham số khảo sát trong mô phỏng này");
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
        if (teacher.getRole() != null && RoleName.MANAGER.matches(teacher.getRole().getName())) return;
        if (teacher.getSchool() == null || student.getSchool() == null
                || !teacher.getSchool().getId().equals(student.getSchool().getId()))
            throw ApiException.forbidden("Giáo viên và học sinh phải thuộc cùng một trường");
        boolean assigned = classEnrollments.findActiveEnrollmentsByStudentId(student.getId()).stream()
                .filter(enrollment -> enrollment.getSchoolClass() != null
                        && enrollment.getSchoolClass().getSchool() != null
                        && teacher.getSchool().getId().equals(enrollment.getSchoolClass().getSchool().getId()))
                .anyMatch(enrollment -> classTeacherAssignments.existsBySchoolClassIdAndTeacherIdAndIsActiveTrue(
                        enrollment.getSchoolClass().getId(), teacher.getId()));
        if (!assigned) throw ApiException.forbidden("Giáo viên chưa được phân công vào lớp của học sinh");
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
        // A closed assignment stays visible to students who handed it in, so their score and feedback are not lost.
        return assignmentRepository.findByAssignedStudentIdsContaining(student.getId()).stream()
                .filter(item -> item.getStatus() == AssignmentStatus.ACTIVE
                        || submissionRepository.findByAssignmentIdAndStudentId(item.getId(), student.getId())
                                .map(submission -> submission.getCompletedAt() != null).orElse(false))
                .map(this::toResponse).toList();
    }

    @Transactional(readOnly = true, noRollbackFor = Exception.class)
    public SimulationResponse simulationForStudent(java.util.UUID assignmentId) {
        User student = currentUserService.requireCurrentUser();
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> ApiException.notFound(ASSIGNMENT_NOT_FOUND));
        if (!assignment.getAssignedStudentIds().contains(student.getId())) {
            throw ApiException.forbidden("Bài tập chưa được giao cho học sinh này");
        }
        if (requiresPrediction(assignment) && !submissionRepository.existsByAssignmentIdAndStudentId(assignmentId, student.getId())) {
            throw ApiException.forbidden("Vui lòng nộp dự đoán trước khi xem mô phỏng");
        }
        if (assignment.getLibraryItem() == null || assignment.getLibraryItem().getSimulation() == null) {
            throw ApiException.notFound("Không tìm thấy mô phỏng đã giao");
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
                                                          SimulationRequests.Adjustment request) {
        User student = currentUserService.requireCurrentUser();
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> ApiException.notFound(ASSIGNMENT_NOT_FOUND));
        if (!assignment.getAssignedStudentIds().contains(student.getId())) {
            throw ApiException.forbidden("Bài tập chưa được giao cho học sinh này");
        }
        if (requiresPrediction(assignment) && !submissionRepository.existsByAssignmentIdAndStudentId(assignmentId, student.getId())) {
            throw ApiException.forbidden("Vui lòng nộp dự đoán trước khi điều chỉnh mô phỏng");
        }
        if (assignment.getLibraryItem() == null || assignment.getLibraryItem().getSimulation() == null) {
            throw ApiException.notFound("Không tìm thấy mô phỏng đã giao");
        }

        var simulation = assignment.getLibraryItem().getSimulation();
        if (request.simulationId() == null || !simulation.getId().equals(request.simulationId())) {
            throw ApiException.badRequest("Mô phỏng không thuộc bài tập này");
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
                .orElseThrow(() -> ApiException.notFound(ASSIGNMENT_NOT_FOUND));
        if (!assignment.getAssignedStudentIds().contains(student.getId())) {
            throw ApiException.forbidden("Bài tập chưa được giao cho học sinh này");
        }
        AssignmentSubmission submission = submissionRepository.findByAssignmentIdAndStudentId(assignmentId, student.getId()).orElse(null);
        if (assignment.getStatus() != com.example.backend.system.assignment.model.enums.AssignmentStatus.ACTIVE)
            throw ApiException.badRequest("Bài tập đã đóng, không nhận thêm bài làm.");
        // Late work remains accepted and is identifiable from dueAt/submittedAt.
        JsonNode prediction = request.predictions();
        if (prediction == null || !prediction.path(ANSWER_TEXT).isTextual()
                || prediction.path(ANSWER_TEXT).asText().isBlank())
            throw ApiException.badRequest("Vui lòng nhập dự đoán");
        if (assignment.isAutoGrade() && !finiteNumber(prediction.path(ESTIMATED_VALUE)))
            throw ApiException.badRequest("Vui lòng nhập dự đoán dạng số cho bài tập này");
        if (submission != null && !submission.isRetryAllowed())
            throw ApiException.conflict("Dự đoán đã được nộp");
        if (submission == null) submission = new AssignmentSubmission();
        submission.setAssignment(assignment);
        submission.setStudent(student);
        submission.setPredictions(request.predictions());
        submission.setSubmittedAt(Instant.now());
        submission.setCompletedAt(null);
        submission.setRetryAllowed(false);
        submission.setGradingStatus(com.example.backend.system.assignment.model.enums.GradingStatus.PENDING);
        submission.setScore(null); submission.setMaxScore(null); submission.setFeedback(null); submission.setGradedAt(null); submission.setGradedBy(null);
        return toSubmission(submissionRepository.save(submission));
    }

    @Transactional
    public AssignmentSubmissionResponse complete(java.util.UUID assignmentId, CompleteAssignmentRequest request) {
        User student = currentUserService.requireCurrentUser();
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> ApiException.notFound(ASSIGNMENT_NOT_FOUND));
        if (!assignment.getAssignedStudentIds().contains(student.getId()))
            throw ApiException.forbidden("Bài tập chưa được giao cho học sinh này");
        if (assignment.getStatus() != com.example.backend.system.assignment.model.enums.AssignmentStatus.ACTIVE)
            throw ApiException.badRequest("Bài tập đã đóng, không nhận thêm bài làm.");
        AssignmentSubmission submission = submissionRepository.findByAssignmentIdAndStudentId(assignmentId, student.getId()).orElse(null);
        if (submission == null && requiresPrediction(assignment))
            throw ApiException.badRequest("Vui lòng nộp dự đoán trước khi hoàn thành bài tập");
        if (submission == null) {
            submission = new AssignmentSubmission();
            submission.setAssignment(assignment);
            submission.setStudent(student);
            submission.setSubmittedAt(Instant.now());
            submission.setPredictions(JsonNodeFactory.instance.objectNode());
        }
        if (submission.getCompletedAt() != null && !submission.isRetryAllowed())
            throw ApiException.conflict("Bài tập đã được nộp");
        if (!(submission.getPredictions() instanceof ObjectNode prediction))
            throw ApiException.badRequest("Nội dung dự đoán không hợp lệ");
        // The prediction made before the simulation opened is the record of a predict-observe-explain
        // activity; completing the work must never replace it with the conclusion.
        boolean keepsPrediction = requiresPrediction(assignment) && prediction.path(ANSWER_TEXT).isTextual()
                && !prediction.path(ANSWER_TEXT).asText().isBlank();
        if (!keepsPrediction) {
            String answer = request.answerText() == null || request.answerText().isBlank()
                    ? request.conclusion().trim() : request.answerText().trim();
            prediction.put(ANSWER_TEXT, answer);
        }
        prediction.put("conclusion", request.conclusion().trim());
        if (request.estimatedValue() != null && Double.isFinite(request.estimatedValue()))
            prediction.put(ESTIMATED_VALUE, request.estimatedValue());
        if (assignment.isAutoGrade() && !finiteNumber(prediction.path(ESTIMATED_VALUE)))
            throw ApiException.badRequest("Vui lòng nhập kết quả đo dạng số cho bài tập này");
        submission.setCompletedAt(Instant.now());
        submission.setRetryAllowed(false);
        submission.setGradingStatus(com.example.backend.system.assignment.model.enums.GradingStatus.PENDING);
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
        submission.setGradingStatus(com.example.backend.system.assignment.model.enums.GradingStatus.AI_GRADED);
        submission.setGradedAt(Instant.now()); submission.setRetryAllowed(false);
    }

    @Transactional
    public AssignmentSubmissionResponse grade(java.util.UUID assignmentId, java.util.UUID submissionId,
                                               BigDecimal score, String feedback, boolean confirm) {
        User teacher = currentUserService.requireCurrentUser();
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> ApiException.notFound(ASSIGNMENT_NOT_FOUND));
        if (!RoleName.MANAGER.matches(teacher.getRole() == null ? null : teacher.getRole().getName())
                && !assignment.getTeacher().getId().equals(teacher.getId()))
            throw ApiException.forbidden("Chỉ giáo viên giao bài mới được chấm bài nộp");
        AssignmentSubmission submission = submissionRepository.findById(submissionId)
                .filter(item -> item.getAssignment().getId().equals(assignmentId))
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy bài nộp"));
        if (submission.getCompletedAt() == null)
            throw ApiException.badRequest("Học sinh chưa nộp bài tập này");
        BigDecimal assignmentMaxScore = assignment.getMaxScore();
        if (score.compareTo(assignmentMaxScore) > 0) throw ApiException.badRequest("Điểm không được vượt quá điểm tối đa của bài tập");
        submission.setScore(score); submission.setMaxScore(assignmentMaxScore); submission.setFeedback(feedback == null ? null : feedback.trim());
        submission.setGradingStatus(confirm ? com.example.backend.system.assignment.model.enums.GradingStatus.TEACHER_CONFIRMED : com.example.backend.system.assignment.model.enums.GradingStatus.AI_GRADED);
        submission.setGradedAt(Instant.now()); submission.setGradedBy(teacher); submission.setRetryAllowed(false);
        return toSubmission(submissionRepository.save(submission));
    }

    @Transactional
    public AssignmentSubmissionResponse reopen(java.util.UUID assignmentId, java.util.UUID submissionId) {
        User teacher = currentUserService.requireCurrentUser();
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> ApiException.notFound(ASSIGNMENT_NOT_FOUND));
        if (!RoleName.MANAGER.matches(teacher.getRole() == null ? null : teacher.getRole().getName())
                && !assignment.getTeacher().getId().equals(teacher.getId()))
            throw ApiException.forbidden("Chỉ giáo viên giao bài mới được mở lại bài nộp");
        AssignmentSubmission submission = submissionRepository.findById(submissionId)
                .filter(item -> item.getAssignment().getId().equals(assignmentId))
                .orElseThrow(() -> ApiException.notFound("Không tìm thấy bài nộp"));
        if (assignment.getStatus() != AssignmentStatus.ACTIVE)
            throw ApiException.badRequest("Bài tập đã đóng. Hãy mở lại bài tập trước khi trả bài cho học sinh.");
        if (submission.getCompletedAt() == null)
            throw ApiException.badRequest("Học sinh chưa nộp bài này nên không thể trả lại.");
        // The old grade belongs to the returned attempt; the new attempt is graded afresh.
        submission.setScore(null); submission.setMaxScore(null); submission.setGradedAt(null); submission.setGradedBy(null);
        submission.setRetryAllowed(true); submission.setGradingStatus(com.example.backend.system.assignment.model.enums.GradingStatus.RETURNED);
        submission.setCompletedAt(null);
        return toSubmission(submissionRepository.save(submission));
    }

    /** Closing stops new work from students; reopening accepts it again. Grades already given are kept. */
    @Transactional
    public AssignmentResponse setStatus(java.util.UUID assignmentId, AssignmentStatus status) {
        User teacher = currentUserService.requireCurrentUser();
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> ApiException.notFound(ASSIGNMENT_NOT_FOUND));
        if (!RoleName.MANAGER.matches(teacher.getRole() == null ? null : teacher.getRole().getName())
                && !assignment.getTeacher().getId().equals(teacher.getId()))
            throw ApiException.forbidden("Chỉ giáo viên giao bài mới được đóng hoặc mở lại bài tập này.");
        assignment.setStatus(status);
        return toResponse(assignmentRepository.save(assignment));
    }

    @Transactional(readOnly = true)
    public List<AssignmentSubmissionResponse> submissions(java.util.UUID assignmentId) {
        User teacher = currentUserService.requireCurrentUser();
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> ApiException.notFound(ASSIGNMENT_NOT_FOUND));
        if (!assignment.getTeacher().getId().equals(teacher.getId())
                && !RoleName.MANAGER.matches(teacher.getRole() == null ? null : teacher.getRole().getName())) {
            throw ApiException.forbidden("Chỉ giáo viên mới được xem bài nộp");
        }
        return submissionRepository.findByAssignmentIdOrderBySubmittedAtDesc(assignmentId).stream().map(this::toSubmission).toList();
    }

    @Transactional(readOnly = true)
    public AssignmentReport report(java.util.UUID assignmentId) {
        User teacher = currentUserService.requireCurrentUser();
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> ApiException.notFound(ASSIGNMENT_NOT_FOUND));
        boolean admin = teacher.getRole() != null && RoleName.MANAGER.matches(teacher.getRole().getName());
        if (!admin && !assignment.getTeacher().getId().equals(teacher.getId()))
            throw ApiException.forbidden("Chỉ giáo viên giao bài mới được xem báo cáo");
        List<AssignmentSubmission> rows = submissionRepository.findByAssignmentIdOrderBySubmittedAtDesc(assignmentId);
        List<AssignmentSubmission> completed = rows.stream().filter(row -> row.getCompletedAt() != null).toList();
        BigDecimal total = completed.stream().map(AssignmentSubmission::getScore).filter(java.util.Objects::nonNull).reduce(BigDecimal.ZERO, BigDecimal::add);
        long graded = completed.stream().filter(row -> row.getGradingStatus() == com.example.backend.system.assignment.model.enums.GradingStatus.AI_GRADED || row.getGradingStatus() == com.example.backend.system.assignment.model.enums.GradingStatus.TEACHER_CONFIRMED).count();
        long confirmed = completed.stream().filter(row -> row.getGradingStatus() == com.example.backend.system.assignment.model.enums.GradingStatus.TEACHER_CONFIRMED).count();
        return new AssignmentReport(assignment.getAssignedStudentIds().size(), completed.size(),
                (long) assignment.getAssignedStudentIds().size() - completed.size(), graded, confirmed,
                rows.stream().filter(AssignmentSubmission::isRetryAllowed).count(),
                graded == 0 ? null : total.divide(BigDecimal.valueOf(graded), 3, java.math.RoundingMode.HALF_UP),
                assignment.getMaxScore());
    }

    private AssignmentResponse toResponse(Assignment item) {
        User current = currentUserService.requireCurrentUser();
        AssignmentSubmission ownSubmission = current.getRole() != null && RoleName.STUDENT.matches(current.getRole().getName())
                ? submissionRepository.findByAssignmentIdAndStudentId(item.getId(), current.getId()).orElse(null) : null;
        boolean predictionSubmitted = ownSubmission != null;
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
