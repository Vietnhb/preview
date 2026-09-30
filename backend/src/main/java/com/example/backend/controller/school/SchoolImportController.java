package com.example.backend.controller.school;

import com.example.backend.dto.school.SchoolImport;
import com.example.backend.service.school.SchoolImportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.Locale;
import java.util.UUID;

@RestController
@RequestMapping("/api/schools/{schoolId}/imports")
@RequiredArgsConstructor
public class SchoolImportController {
    private final SchoolImportService service;

    @GetMapping(value = "/template", produces = "text/csv")
    public ResponseEntity<byte[]> template(@PathVariable UUID schoolId, @RequestParam SchoolImport.Kind kind) {
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .header("Content-Disposition", "attachment; filename=\"school-" + kind.name().toLowerCase(Locale.ROOT) + "-template.csv\"")
                .body(service.template(schoolId, kind));
    }

    @PostMapping(value = "/preview", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SchoolImport.Preview previewFile(@PathVariable UUID schoolId, @RequestParam SchoolImport.Kind kind,
                                           @RequestPart("file") MultipartFile file) {
        return service.previewFile(schoolId, kind, file);
    }

    @PostMapping(value = "/preview", consumes = MediaType.APPLICATION_JSON_VALUE)
    public SchoolImport.Preview previewRows(@PathVariable UUID schoolId, @RequestBody SchoolImport.Request request) {
        return service.preview(schoolId, request);
    }

    @PostMapping(value = "/commit", consumes = MediaType.APPLICATION_JSON_VALUE)
    public SchoolImport.Result commit(@PathVariable UUID schoolId, @RequestBody SchoolImport.Request request) {
        return service.commit(schoolId, request);
    }
}
