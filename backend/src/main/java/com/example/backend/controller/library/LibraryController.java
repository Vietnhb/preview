package com.example.backend.controller.library;

import com.example.backend.dto.library.CloneLibraryItemRequest;
import com.example.backend.dto.library.LibraryItemResponse;
import com.example.backend.dto.library.LibrarySaveRequest;
import com.example.backend.dto.library.MoveLibraryItemRequest;
import com.example.backend.dto.library.RenameLibraryItemRequest;
import com.example.backend.service.library.LibraryService;
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

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/library")
@RequiredArgsConstructor
public class LibraryController {
    private final LibraryService libraryService;

    @org.springframework.web.bind.annotation.PatchMapping("/{id}")
    public LibraryItemResponse rename(@PathVariable UUID id, @Valid @RequestBody RenameLibraryItemRequest request) {
        return libraryService.rename(id, request.title());
    }

    @org.springframework.web.bind.annotation.PatchMapping("/{id}/folder")
    public LibraryItemResponse move(@PathVariable UUID id, @Valid @RequestBody MoveLibraryItemRequest request) {
        return libraryService.move(id, request.folderId());
    }

    @PostMapping
    public LibraryItemResponse save(@Valid @RequestBody LibrarySaveRequest request) {
        return libraryService.save(request);
    }

    @PostMapping("/{id}/clone")
    public LibraryItemResponse clone(@PathVariable UUID id, @Valid @RequestBody CloneLibraryItemRequest request) {
        return libraryService.cloneShared(id, request.folderId(), request.title());
    }

    @GetMapping
    public List<LibraryItemResponse> search(@RequestParam(required = false) String topic) {
        return libraryService.search(topic);
    }

    @GetMapping("/community")
    public List<LibraryItemResponse> community(@RequestParam(required = false) String topic) {
        return libraryService.community(topic);
    }

    @GetMapping("/mine")
    public List<LibraryItemResponse> mine() {
        return libraryService.mine();
    }

    @DeleteMapping("/{id}")
    public void remove(@PathVariable UUID id) {
        libraryService.remove(id);
    }
}
