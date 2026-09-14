package com.example.backend.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.backend.dto.curriculum.CurriculumTreeResponse;
import com.example.backend.service.CurriculumService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/curriculum")
@RequiredArgsConstructor
public class CurriculumController {

    private final CurriculumService curriculumService;

    @GetMapping
    public CurriculumTreeResponse getTree(@RequestParam(defaultValue = "false") boolean includeInactive) {
        return curriculumService.getTree(includeInactive);
    }
}
