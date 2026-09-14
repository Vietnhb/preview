package com.example.backend.service;

import com.example.backend.dto.assignment.AssignmentResponse;
import com.example.backend.dto.assignment.AssignmentSubmissionResponse;
import com.example.backend.dto.assignment.CreateAssignmentRequest;
import com.example.backend.dto.assignment.SubmitPredictionRequest;
import com.example.backend.entity.Assignment;
import com.example.backend.entity.AssignmentSubmission;
import com.example.backend.entity.Specification;
import com.example.backend.entity.LibraryItem;
import com.example.backend.entity.SimulationStatus;
import com.example.backend.entity.User;
import com.example.backend.exception.ApiException;
import com.example.backend.repository.AssignmentRepository;
import com.example.backend.repository.AssignmentSubmissionRepository;
import com.example.backend.repository.LibraryItemRepository;
import com.example.backend.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
public class AssignmentService {
    private final AssignmentRepository assignmentRepository;
    private final AssignmentSubmissionRepository submissionRepository;
    private final LibraryItemRepository libraryItemRepository;
    private final UserRepository userRepository;
    private final CurrentUserService currentUserService;

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
            if (!"STUDENT".equalsIgnoreCase(role)) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Every assignee must have STUDENT role");
            }
        }
        Assignment assignment = new Assignment();
        assignment.setLibraryItem(libraryItem);
        assignment.setSpecification(specification);
        assignment.setTeacher(teacher);
        assignment.setTitle(request.title().trim());
        assignment.setDescription(request.description());
        assignment.setQuestions(request.questions());
        assignment.setAssignedStudentIds(request.studentIds());
        assignment.setDueAt(request.dueAt());
        assignment.setAssignedAt(Instant.now());
        return toResponse(assignmentRepository.save(assignment));
    }

    @Transactional(readOnly = true)
    public List<AssignmentResponse> forTeacher() {
        User teacher = currentUserService.requireCurrentUser();
        return assignmentRepository.findByTeacherIdOrderByCreatedAtDesc(teacher.getId()).stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<AssignmentResponse> forStudent() {
        User student = currentUserService.requireCurrentUser();
        return assignmentRepository.findByAssignedStudentIdsContaining(student.getId()).stream().map(this::toResponse).toList();
    }

    @Transactional
    public AssignmentSubmissionResponse submit(java.util.UUID assignmentId, SubmitPredictionRequest request) {
        User student = currentUserService.requireCurrentUser();
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Assignment not found"));
        if (!assignment.getAssignedStudentIds().contains(student.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Assignment is not assigned to this student");
        }
        if (submissionRepository.existsByAssignmentIdAndStudentId(assignmentId, student.getId())) {
            throw new ApiException(HttpStatus.CONFLICT, "Prediction already submitted");
        }
        AssignmentSubmission submission = new AssignmentSubmission();
        submission.setAssignment(assignment);
        submission.setStudent(student);
        submission.setPredictions(request.predictions());
        submission.setSubmittedAt(Instant.now());
        return toSubmission(submissionRepository.save(submission));
    }

    @Transactional(readOnly = true)
    public List<AssignmentSubmissionResponse> submissions(java.util.UUID assignmentId) {
        User teacher = currentUserService.requireCurrentUser();
        Assignment assignment = assignmentRepository.findById(assignmentId)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Assignment not found"));
        if (!assignment.getTeacher().getId().equals(teacher.getId())) {
            throw new ApiException(HttpStatus.FORBIDDEN, "Only the teacher can view submissions");
        }
        return submissionRepository.findByAssignmentIdOrderBySubmittedAtDesc(assignmentId).stream().map(this::toSubmission).toList();
    }

    private AssignmentResponse toResponse(Assignment item) {
        return new AssignmentResponse(item.getId(), item.getLibraryItem() == null ? null : item.getLibraryItem().getId(),
                item.getSpecification().getId(), item.getTitle(), item.getDescription(),
                item.getQuestions(), item.getAssignedStudentIds(), item.getStatus(), item.getAssignedAt(), item.getDueAt());
    }

    private AssignmentSubmissionResponse toSubmission(AssignmentSubmission item) {
        return new AssignmentSubmissionResponse(item.getId(), item.getAssignment().getId(), item.getStudent().getId(),
                item.getStudent().getFullName(), item.getPredictions(), item.getSubmittedAt());
    }
}
