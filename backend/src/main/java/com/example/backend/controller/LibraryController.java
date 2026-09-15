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

    public record MoveRequest(@jakarta.validation.constraints.NotNull UUID folderId) {}
    public record RenameRequest(@jakarta.validation.constraints.NotBlank @jakarta.validation.constraints.Size(max = 160) String title) {}

    @org.springframework.web.bind.annotation.PatchMapping("/{id}")
    @PreAuthorize("hasRole('TEACHER')")
    public LibraryItemResponse rename(@PathVariable UUID id, @Valid @RequestBody RenameRequest request) {
        return libraryService.rename(id, request.title());
    }

    @org.springframework.web.bind.annotation.PatchMapping("/{id}/folder")
    @PreAuthorize("hasRole('TEACHER')")
    public LibraryItemResponse move(@PathVariable UUID id, @Valid @RequestBody MoveRequest request) {
        return libraryService.move(id, request.folderId());
    }

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
