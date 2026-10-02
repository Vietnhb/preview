package com.example.backend.system.library.controller;

import com.example.backend.system.library.dto.LibraryDiscussion;
import com.example.backend.system.library.dto.LibraryItemResponse;
import com.example.backend.system.library.dto.LibraryRequests;
import com.example.backend.system.library.service.LibraryDiscussionService;
import com.example.backend.system.library.service.LibraryService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/library")
@RequiredArgsConstructor
public class LibraryController {
    private final LibraryService libraryService;
    private final LibraryDiscussionService discussionService;

    @GetMapping("/{id}/discussion")
    public LibraryDiscussion.Discussion discussion(@PathVariable UUID id,
            @RequestParam(defaultValue = "0") int page, @RequestParam(defaultValue = "20") int size) {
        return discussionService.discussion(id, page, size);
    }

    @PostMapping("/{id}/comments")
    public LibraryDiscussion.Comment comment(@PathVariable UUID id,
            @Valid @RequestBody LibraryDiscussion.CommentRequest request) {
        return discussionService.comment(id, request.body());
    }

    @DeleteMapping("/{id}/comments/{commentId}")
    public void removeComment(@PathVariable UUID id, @PathVariable UUID commentId) {
        discussionService.removeComment(id, commentId);
    }

    @PutMapping("/{id}/reaction")
    public LibraryDiscussion.Reaction react(@PathVariable UUID id,
            @Valid @RequestBody LibraryDiscussion.ReactionRequest request) {
        return discussionService.react(id, request.liked());
    }

    @org.springframework.web.bind.annotation.PatchMapping("/{id}")
    public LibraryItemResponse rename(@PathVariable UUID id, @Valid @RequestBody LibraryRequests.Rename request) {
        return libraryService.rename(id, request.title());
    }

    @org.springframework.web.bind.annotation.PatchMapping("/{id}/folder")
    public LibraryItemResponse move(@PathVariable UUID id, @Valid @RequestBody LibraryRequests.Move request) {
        return libraryService.move(id, request.folderId());
    }

    @PostMapping
    public LibraryItemResponse save(@Valid @RequestBody LibraryRequests.Save request) {
        return libraryService.save(request);
    }

    @PostMapping("/{id}/clone")
    public LibraryItemResponse clone(@PathVariable UUID id, @Valid @RequestBody LibraryRequests.Clone request) {
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
