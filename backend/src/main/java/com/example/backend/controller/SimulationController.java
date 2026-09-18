package com.example.backend.controller;

import com.example.backend.dto.physics.ParameterAdjustmentRequest;
import com.example.backend.dto.physics.SimulationRequest;
import com.example.backend.dto.physics.SimulationResponse;
import com.example.backend.dto.physics.SimulationSummaryResponse;
import com.example.backend.service.SimulationService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/simulations")
@RequiredArgsConstructor
public class SimulationController {
    private final SimulationService simulationService;

    @PostMapping
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public ResponseEntity<SimulationResponse> run(@Valid @RequestBody SimulationRequest request) {
        return ResponseEntity.status(HttpStatus.CREATED).body(simulationService.run(request));
    }

    @PostMapping("/adjust")
    @org.springframework.security.access.prepost.PreAuthorize("hasAnyRole('TEACHER','ADMIN')")
    public SimulationResponse adjust(@Valid @RequestBody ParameterAdjustmentRequest request) {
        return simulationService.adjust(request);
    }

    @GetMapping
    public List<SimulationResponse> history() {
        return simulationService.history();
    }

    @GetMapping("/recent")
    public List<SimulationSummaryResponse> recent() {
        return simulationService.recent();
    }

    @GetMapping("/{id}")
    public SimulationResponse get(@PathVariable UUID id) {
        return simulationService.get(id);
    }

    @GetMapping("/shared/{id}")
    public SimulationResponse getShared(@PathVariable UUID id) {
        return simulationService.getShared(id);
    }
}
