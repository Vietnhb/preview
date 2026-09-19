package com.example.backend.controller.assignment;

import com.example.backend.dto.assignment.AssignmentResponse;
import com.example.backend.dto.assignment.AssignmentSubmissionResponse;
import com.example.backend.dto.assignment.CreateAssignmentRequest;
import com.example.backend.dto.assignment.SubmitPredictionRequest;
import com.example.backend.dto.simulation.ParameterAdjustmentRequest;
import com.example.backend.dto.simulation.SimulationResponse;
import com.example.backend.service.assignment.AssignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.UUID;
import java.math.BigDecimal;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

@RestController
@RequestMapping("/api/assignments")
@RequiredArgsConstructor
public class AssignmentController {
    private final AssignmentService assignmentService;

    public record GradeRequest(@NotNull @DecimalMin("0.0") BigDecimal score,
                               @NotNull @DecimalMin("0.001") BigDecimal maxScore,
                               @Size(max = 4000) String feedback, boolean confirm) { }

    @PostMapping
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public AssignmentResponse create(@Valid @RequestBody CreateAssignmentRequest request) {
        return assignmentService.create(request);
    }

    @GetMapping("/mine/teacher")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public List<AssignmentResponse> teacherAssignments() {
        return assignmentService.forTeacher();
    }

    @GetMapping("/mine/student")
    @PreAuthorize("hasRole('STUDENT')")
    public List<AssignmentResponse> studentAssignments() {
        return assignmentService.forStudent();
    }

    @PostMapping("/{id}/predictions")
    @PreAuthorize("hasRole('STUDENT')")
    public AssignmentSubmissionResponse submit(@PathVariable UUID id,
                                               @Valid @RequestBody SubmitPredictionRequest request) {
        return assignmentService.submit(id, request);
    }

    @GetMapping("/{id}/simulation")
    @PreAuthorize("hasRole('STUDENT')")
    public SimulationResponse simulation(@PathVariable UUID id) {
        return assignmentService.simulationForStudent(id);
    }

    @PostMapping("/{id}/simulation/adjust")
    @PreAuthorize("hasRole('STUDENT')")
    public SimulationResponse adjustSimulation(@PathVariable UUID id,
                                                @Valid @RequestBody ParameterAdjustmentRequest request) {
        return assignmentService.adjustSimulationForStudent(id, request);
    }

    @GetMapping("/{id}/submissions")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public List<AssignmentSubmissionResponse> submissions(@PathVariable UUID id) {
        return assignmentService.submissions(id);
    }

    @GetMapping("/{id}/report")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public AssignmentService.AssignmentReport report(@PathVariable UUID id) {
        return assignmentService.report(id);
    }

    @PutMapping("/{assignmentId}/submissions/{submissionId}/grade")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public AssignmentSubmissionResponse grade(@PathVariable UUID assignmentId,
                                               @PathVariable UUID submissionId,
                                               @Valid @RequestBody GradeRequest request) {
        return assignmentService.grade(assignmentId, submissionId, request.score(), request.maxScore(), request.feedback(), request.confirm());
    }

    @PostMapping("/{assignmentId}/submissions/{submissionId}/reopen")
    @PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public AssignmentSubmissionResponse reopen(@PathVariable UUID assignmentId, @PathVariable UUID submissionId) {
        return assignmentService.reopen(assignmentId, submissionId);
    }
}
