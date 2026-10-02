package com.example.backend.system.library.controller;

import com.example.backend.system.library.dto.LibraryFolders;
import com.example.backend.system.library.service.LibraryFolderService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/library/folders")
@RequiredArgsConstructor
public class LibraryFolderController {
    private final LibraryFolderService folderService;

    @GetMapping
    public List<LibraryFolders.Response> mine() {
        return folderService.mine();
    }

    @PostMapping
    public LibraryFolders.Response create(@Valid @RequestBody LibraryFolders.Request request) {
        return folderService.create(request);
    }

    @PatchMapping("/{id}")
    public LibraryFolders.Response rename(@PathVariable UUID id, @Valid @RequestBody LibraryFolders.Request request) {
        return folderService.rename(id, request);
    }

    @DeleteMapping("/{id}")
    public void remove(@PathVariable UUID id) {
        folderService.remove(id);
    }
}
