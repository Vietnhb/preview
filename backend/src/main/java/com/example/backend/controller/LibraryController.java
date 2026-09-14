package com.example.backend.controller;

import com.example.backend.dto.library.LibraryItemResponse;
import com.example.backend.dto.library.LibrarySaveRequest;
import com.example.backend.service.LibraryService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.security.access.prepost.PreAuthorize;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/library")
@RequiredArgsConstructor
public class LibraryController {
    private final LibraryService libraryService;

    @PostMapping
    @PreAuthorize("hasRole('TEACHER')")
    public LibraryItemResponse save(@Valid @RequestBody LibrarySaveRequest request) {
        return libraryService.save(request);
    }

    @GetMapping
    public List<LibraryItemResponse> search(@RequestParam(required = false) String topic) {
        return libraryService.search(topic);
    }

    @GetMapping("/mine")
    @PreAuthorize("hasRole('TEACHER')")
    public List<LibraryItemResponse> mine() {
        return libraryService.mine();
    }

    @DeleteMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public void remove(@PathVariable UUID id) {
        libraryService.remove(id);
    }
}
