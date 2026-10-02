package com.example.backend.system.school.controller;

import com.example.backend.system.school.dto.SchoolClassContracts;
import com.example.backend.system.school.service.SchoolClassService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/student/classes")
@RequiredArgsConstructor
public class StudentClassController {
    private final SchoolClassService service;

    @GetMapping
    public List<SchoolClassContracts.StudentClassSummary> mine() {
        return service.mineForStudent();
    }
}
