package com.example.backend.controller.support;

import com.example.backend.dto.support.CreateSupportRequest;
import com.example.backend.dto.support.UpdateSupportRequest;
import com.example.backend.entity.enums.SupportKind;
import com.example.backend.service.support.SupportService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/support")
@RequiredArgsConstructor
public class SupportController {
    private final SupportService service;

    @PostMapping("/feedback")
    public SupportService.SupportView feedback(@Valid @RequestBody CreateSupportRequest request) { return service.create(SupportKind.FEEDBACK, request); }

    @PostMapping("/messages")
    public SupportService.SupportView message(@Valid @RequestBody CreateSupportRequest request) { return service.create(SupportKind.MESSAGE, request); }

    @GetMapping("/mine")
    public List<SupportService.SupportView> mine() { return service.mine(); }

    @GetMapping("/admin")
    public List<SupportService.SupportView> adminList(@RequestParam SupportKind kind) { return service.adminList(kind); }

    @PutMapping("/admin/{id}")
    public SupportService.SupportView update(@PathVariable UUID id,
                                             @Valid @RequestBody UpdateSupportRequest request) {
        return service.update(id, request);
    }
}
