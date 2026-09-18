package com.example.backend.controller;

import com.example.backend.dto.library.LibraryFolderRequest;
import com.example.backend.dto.library.LibraryFolderResponse;
import com.example.backend.service.LibraryFolderService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/library/folders")
@RequiredArgsConstructor
@PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
public class LibraryFolderController {
    private final LibraryFolderService folderService;

    @GetMapping
    public List<LibraryFolderResponse> mine() {
        return folderService.mine();
    }

    @PostMapping
    public LibraryFolderResponse create(@Valid @RequestBody LibraryFolderRequest request) {
        return folderService.create(request);
    }

    @PatchMapping("/{id}")
    public LibraryFolderResponse rename(@PathVariable UUID id, @Valid @RequestBody LibraryFolderRequest request) {
        return folderService.rename(id, request);
    }

    @DeleteMapping("/{id}")
    public void remove(@PathVariable UUID id) {
        folderService.remove(id);
    }
}
