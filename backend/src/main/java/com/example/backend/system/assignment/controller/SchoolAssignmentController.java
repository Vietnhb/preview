package com.example.backend.system.assignment.controller;

import com.example.backend.system.assignment.dto.AssignmentContracts.SchoolAssignment;
import com.example.backend.system.assignment.dto.AssignmentContracts;
import com.example.backend.system.assignment.service.SchoolAssignmentService;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/schools/{schoolId}/assignments")
@RequiredArgsConstructor
public class SchoolAssignmentController {
    private final SchoolAssignmentService service;

    @GetMapping
    public Page<SchoolAssignment> list(@PathVariable UUID schoolId,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "30") int size) {
        return service.list(schoolId, page, size);
    }
}
