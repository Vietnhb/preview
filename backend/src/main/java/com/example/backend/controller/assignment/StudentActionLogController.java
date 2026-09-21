package com.example.backend.controller.assignment;

import com.example.backend.dto.assignment.StudentActionLogRequest;
import com.example.backend.entity.audit.StudentActionLog;
import com.example.backend.service.assignment.StudentActionLogService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/student/action-logs")
@RequiredArgsConstructor
public class StudentActionLogController {
    private final StudentActionLogService service;

    @PostMapping
    public StudentActionLog create(@Valid @RequestBody StudentActionLogRequest request) {
        return service.create(request);
    }

    @GetMapping
    public List<StudentActionLog> mine() {
        return service.mine();
    }
}
