package com.example.backend.system.assignment.controller;

import com.example.backend.system.assignment.dto.AssignmentContracts.StudentActionLogRequest;
import com.example.backend.system.assignment.dto.AssignmentContracts.StudentActionLogResponse;
import com.example.backend.system.assignment.dto.AssignmentContracts;
import com.example.backend.system.assignment.service.StudentActionLogService;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student/action-logs")
@RequiredArgsConstructor
public class StudentActionLogController {
    private final StudentActionLogService service;

    @PostMapping
    public StudentActionLogResponse create(@Valid @RequestBody StudentActionLogRequest request) {
        return service.create(request);
    }

    @GetMapping
    public List<StudentActionLogResponse> mine() {
        return service.mine();
    }
}
