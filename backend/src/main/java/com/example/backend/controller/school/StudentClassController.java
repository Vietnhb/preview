package com.example.backend.controller.school;

import com.example.backend.service.school.SchoolClassService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/student/classes")
@PreAuthorize("hasRole('STUDENT')")
@RequiredArgsConstructor
public class StudentClassController {
    private final SchoolClassService service;

    @GetMapping
    public List<SchoolClassService.StudentClassSummary> mine() {
        return service.mineForStudent();
    }
}
