package com.example.backend.controller.curriculum;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.example.backend.dto.curriculum.CurriculumTreeResponse;
import com.example.backend.service.curriculum.CurriculumService;
import com.example.backend.service.problem.SchemaDefinitionService;

import lombok.RequiredArgsConstructor;

@RestController
@RequestMapping("/api/curriculum")
@RequiredArgsConstructor
public class CurriculumController {

    private final CurriculumService curriculumService;
    private final SchemaDefinitionService schemas;

    @GetMapping
    public CurriculumTreeResponse getTree(@RequestParam(defaultValue = "false") boolean includeInactive,
            @RequestParam(required = false) String schemaId,
            @RequestParam(required = false) String schemaVersion) {
        if (schemaId == null || schemaId.isBlank()) return curriculumService.getTree(includeInactive);
        // Use the same authoritative topic as the save endpoint, not client display metadata.
        String topic = schemas.requireCurrentApproved(schemaId, schemaVersion).getTopic();
        return curriculumService.getActiveTreeForTopic(topic);
    }
}
