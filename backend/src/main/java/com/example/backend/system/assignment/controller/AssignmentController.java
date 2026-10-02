package com.example.backend.system.assignment.controller;

import com.example.backend.system.assignment.dto.AssignmentContracts.AssignmentResponse;
import com.example.backend.system.assignment.dto.AssignmentContracts.AssignmentSubmissionResponse;
import com.example.backend.system.assignment.dto.AssignmentContracts.CompleteAssignmentRequest;
import com.example.backend.system.assignment.dto.AssignmentContracts.CreateAssignmentRequest;
import com.example.backend.system.assignment.dto.AssignmentContracts.GradeAssignmentRequest;
import com.example.backend.system.assignment.dto.AssignmentContracts.SubmitPredictionRequest;
import com.example.backend.system.assignment.dto.AssignmentContracts;
import com.example.backend.system.assignment.service.AssignmentService;
import com.example.backend.system.simulation.dto.SimulationRequests;
import com.example.backend.system.simulation.dto.SimulationResponse;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/assignments")
@RequiredArgsConstructor
public class AssignmentController {
    private final AssignmentService assignmentService;

    @PostMapping
    public AssignmentResponse create(@Valid @RequestBody CreateAssignmentRequest request) {
        return assignmentService.create(request);
    }

    @GetMapping("/mine/teacher")
    public List<AssignmentResponse> teacherAssignments() {
        return assignmentService.forTeacher();
    }

    @GetMapping("/mine/teacher/classes")
    public List<AssignmentContracts.TeacherClassOption> teacherClasses() {
        return assignmentService.classesForTeacher();
    }

    @GetMapping("/mine/student")
    public List<AssignmentResponse> studentAssignments() {
        return assignmentService.forStudent();
    }

    @PostMapping("/{id}/predictions")
    public AssignmentSubmissionResponse submit(@PathVariable UUID id,
                                               @Valid @RequestBody SubmitPredictionRequest request) {
        return assignmentService.submit(id, request);
    }

    @PostMapping("/{id}/submit")
    public AssignmentSubmissionResponse complete(@PathVariable UUID id,
                                                  @Valid @RequestBody CompleteAssignmentRequest request) {
        return assignmentService.complete(id, request);
    }

    @GetMapping("/{id}/simulation")
    public SimulationResponse simulation(@PathVariable UUID id) {
        return assignmentService.simulationForStudent(id);
    }

    @PostMapping("/{id}/simulation/adjust")
    public SimulationResponse adjustSimulation(@PathVariable UUID id,
                                                @Valid @RequestBody SimulationRequests.Adjustment request) {
        return assignmentService.adjustSimulationForStudent(id, request);
    }

    @GetMapping("/{id}/submissions")
    public List<AssignmentSubmissionResponse> submissions(@PathVariable UUID id) {
        return assignmentService.submissions(id);
    }

    @GetMapping("/{id}/report")
    public AssignmentContracts.AssignmentReport report(@PathVariable UUID id) {
        return assignmentService.report(id);
    }

    @PutMapping("/{assignmentId}/submissions/{submissionId}/grade")
    public AssignmentSubmissionResponse grade(@PathVariable UUID assignmentId,
                                               @PathVariable UUID submissionId,
                                               @Valid @RequestBody GradeAssignmentRequest request) {
        return assignmentService.grade(assignmentId, submissionId, request.score(), request.feedback(), request.confirm());
    }

    @PostMapping("/{assignmentId}/submissions/{submissionId}/reopen")
    public AssignmentSubmissionResponse reopen(@PathVariable UUID assignmentId, @PathVariable UUID submissionId) {
        return assignmentService.reopen(assignmentId, submissionId);
    }
}
