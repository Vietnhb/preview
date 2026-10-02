package com.example.backend.system.support.controller;

import com.example.backend.system.support.dto.SupportContracts.CreateSupportRequest;
import com.example.backend.system.support.dto.SupportContracts.UpdateSupportRequest;
import com.example.backend.system.support.dto.SupportContracts;
import com.example.backend.system.support.model.enums.SupportKind;
import com.example.backend.system.support.service.SupportService;
import jakarta.validation.Valid;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/support")
@RequiredArgsConstructor
public class SupportController {
    private final SupportService service;

    @PostMapping("/feedback")
    public SupportContracts.SupportView feedback(@Valid @RequestBody CreateSupportRequest request) { return service.create(SupportKind.FEEDBACK, request); }

    @PostMapping("/messages")
    public SupportContracts.SupportView message(@Valid @RequestBody CreateSupportRequest request) { return service.create(SupportKind.MESSAGE, request); }

    /** A teacher reports a simulation as wrong; reviewers answer it. */
    @PostMapping("/complaints")
    public SupportContracts.SupportView complaint(@Valid @RequestBody SupportContracts.CreateComplaintRequest request) {
        return service.createComplaint(request);
    }
    @GetMapping("/complaints/review")
    public List<SupportContracts.SupportView> complaintsForReview() { return service.complaintsForReview(); }
    @PutMapping("/complaints/review/{id}")
    public SupportContracts.SupportView resolveComplaint(@PathVariable UUID id,
                                                       @Valid @RequestBody UpdateSupportRequest request) {
        return service.resolveComplaint(id, request);
    }
    @GetMapping("/mine")
    public List<SupportContracts.SupportView> mine() { return service.mine(); }

    @GetMapping("/admin")
    public List<SupportContracts.SupportView> adminList(@RequestParam SupportKind kind) { return service.adminList(kind); }

    @PutMapping("/admin/{id}")
    public SupportContracts.SupportView update(@PathVariable UUID id,
                                             @Valid @RequestBody UpdateSupportRequest request) {
        return service.update(id, request);
    }
}
