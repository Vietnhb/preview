package com.example.backend.controller;

import com.example.backend.dto.session.SaveSessionRequest;
import com.example.backend.dto.session.SessionResponse;
import com.example.backend.service.SessionService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/sessions")
@RequiredArgsConstructor
public class SessionController {
    private final SessionService sessionService;

    @PostMapping
    public SessionResponse save(@Valid @RequestBody SaveSessionRequest request) {
        return sessionService.save(request);
    }

    @GetMapping
    public List<SessionResponse> mine() {
        return sessionService.mine();
    }

    @GetMapping("/{id}")
    public SessionResponse get(@PathVariable UUID id) {
        return sessionService.get(id);
    }
}
