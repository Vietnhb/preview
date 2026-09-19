package com.example.backend.controller;

import com.example.backend.service.SchoolClassService;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/schools/{schoolId}/classes")
@PreAuthorize("hasAnyRole('SCHOOL_MANAGER', 'ADMIN')")
@RequiredArgsConstructor
public class SchoolClassController {
    private final SchoolClassService service;

    public record StudentRequest(@NotNull Integer studentId) { }
    public record TeacherRequest(@NotNull Integer teacherId) { }

    @GetMapping
    public List<SchoolClassService.ClassSummary> list(@PathVariable UUID schoolId) { return service.list(schoolId); }
    @GetMapping("/{classId}")
    public SchoolClassService.ClassDetail get(@PathVariable UUID schoolId, @PathVariable UUID classId) { return service.get(schoolId, classId); }
    @PostMapping
    public SchoolClassService.ClassDetail create(@PathVariable UUID schoolId, @Valid @RequestBody SchoolClassService.ClassRequest request) { return service.create(schoolId, request); }
    @PutMapping("/{classId}")
    public SchoolClassService.ClassDetail update(@PathVariable UUID schoolId, @PathVariable UUID classId, @Valid @RequestBody SchoolClassService.ClassRequest request) { return service.update(schoolId, classId, request); }
    @DeleteMapping("/{classId}")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void archive(@PathVariable UUID schoolId, @PathVariable UUID classId) { service.archive(schoolId, classId); }
    @PostMapping("/{classId}/teachers")
    public SchoolClassService.TeacherAssignment assignTeacher(@PathVariable UUID schoolId, @PathVariable UUID classId, @Valid @RequestBody TeacherRequest request) { return service.assignTeacher(schoolId, classId, request.teacherId()); }
    @DeleteMapping("/{classId}/teachers/{teacherId}")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void unassignTeacher(@PathVariable UUID schoolId, @PathVariable UUID classId, @PathVariable Integer teacherId) { service.unassignTeacher(schoolId, classId, teacherId); }
    @PostMapping("/{classId}/students")
    public SchoolClassService.Enrollment enroll(@PathVariable UUID schoolId, @PathVariable UUID classId, @Valid @RequestBody StudentRequest request) { return service.enrollStudent(schoolId, classId, request.studentId()); }
    @PutMapping("/{classId}/students/{studentId}/transfer")
    public SchoolClassService.Enrollment transfer(@PathVariable UUID schoolId, @PathVariable UUID classId, @PathVariable Integer studentId) { return service.transferStudent(schoolId, classId, studentId); }
    @DeleteMapping("/{classId}/students/{studentId}")
    @ResponseStatus(org.springframework.http.HttpStatus.NO_CONTENT)
    public void remove(@PathVariable UUID schoolId, @PathVariable UUID classId, @PathVariable Integer studentId) { service.removeStudent(schoolId, classId, studentId); }
}
