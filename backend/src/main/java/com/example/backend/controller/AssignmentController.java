package com.example.backend.controller;

import com.example.backend.dto.assignment.AssignmentResponse;
import com.example.backend.dto.assignment.AssignmentSubmissionResponse;
import com.example.backend.dto.assignment.CreateAssignmentRequest;
import com.example.backend.dto.assignment.SubmitPredictionRequest;
import com.example.backend.dto.physics.SimulationResponse;
import com.example.backend.service.AssignmentService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/assignments")
@RequiredArgsConstructor
public class AssignmentController {
    private final AssignmentService assignmentService;

    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    public AssignmentResponse create(@Valid @RequestBody CreateAssignmentRequest request) {
        return assignmentService.create(request);
    }

    @GetMapping("/mine/teacher")
    @PreAuthorize("hasRole('TEACHER')")
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

    @GetMapping("/{id}/submissions")
    @PreAuthorize("hasRole('TEACHER')")
    public List<AssignmentSubmissionResponse> submissions(@PathVariable UUID id) {
        return assignmentService.submissions(id);
    }
}
