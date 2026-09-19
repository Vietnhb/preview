package com.example.backend.controller.school;

import com.example.backend.service.school.SchoolReportService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/schools/{schoolId}/reports")
@PreAuthorize("hasAnyRole('SCHOOL_MANAGER','ADMIN')")
@RequiredArgsConstructor
public class SchoolReportController {
    private final SchoolReportService service;

    @GetMapping("/summary")
    public SchoolReportService.Summary summary(@PathVariable UUID schoolId) {
        return service.summary(schoolId);
    }

    @GetMapping("/classes")
    public List<SchoolReportService.ClassRow> classes(@PathVariable UUID schoolId) {
        return service.classes(schoolId);
    }

    @GetMapping("/token-audit")
    public List<SchoolReportService.TokenRow> tokenAudit(@PathVariable UUID schoolId) {
        return service.tokenAudit(schoolId);
    }

    @PostMapping(value = "/users/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public SchoolReportService.ImportResult importUsers(@PathVariable UUID schoolId,
            @RequestPart("file") MultipartFile file) {
        return service.importUsers(schoolId, file);
    }

    @GetMapping(value = "/classes.csv", produces = "text/csv")
    public ResponseEntity<byte[]> exportClasses(@PathVariable UUID schoolId) {
        StringBuilder csv = new StringBuilder("name,gradeLevel,schoolYear,teachers,students\n");
        service.classes(schoolId)
                .forEach(row -> csv.append(csv(row.name())).append(',').append(row.gradeLevel()).append(',')
                        .append(csv(row.schoolYear())).append(',').append(row.teachers()).append(',')
                        .append(row.students()).append('\n'));
        return ResponseEntity.ok().contentType(MediaType.parseMediaType("text/csv; charset=UTF-8"))
                .body(csv.toString().getBytes(StandardCharsets.UTF_8));
    }

    private static String csv(String value) {
        return "\"" + value.replace("\"", "\"\"") + "\"";
    }
}
