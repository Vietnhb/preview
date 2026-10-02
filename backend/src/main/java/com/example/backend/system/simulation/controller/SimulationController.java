package com.example.backend.system.simulation.controller;

import com.example.backend.system.simulation.dto.SimulationResponse;
import com.example.backend.system.simulation.dto.SimulationSummaryResponse;
import com.example.backend.system.simulation.service.SimulationService;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/simulations")
@RequiredArgsConstructor
public class SimulationController {
    private final SimulationService simulationService;

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
