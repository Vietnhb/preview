package com.example.backend.system.curriculum.controller;

import com.example.backend.system.curriculum.dto.CurriculumTreeResponse;
import com.example.backend.system.curriculum.service.CurriculumService;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/curriculum")
@RequiredArgsConstructor
public class CurriculumController {

    private final CurriculumService curriculumService;

    @GetMapping
    public CurriculumTreeResponse getTree(@RequestParam(defaultValue = "false") boolean includeInactive,
            @RequestParam(required = false) String schemaId,
            @RequestParam(required = false) String schemaVersion) {
        return curriculumService.getTree(includeInactive, schemaId, schemaVersion);
    }
}
