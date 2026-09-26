package com.example.backend.controller.simulation;

import com.example.backend.dto.simulation.ParameterAdjustmentRequest;
import com.example.backend.dto.simulation.SimulationResponse;
import com.example.backend.dto.simulation.SimulationSummaryResponse;
import com.example.backend.service.simulation.SimulationService;
import com.example.backend.exception.ApiException;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
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
    public SimulationResponse run() {
        throw legacyExecutionGone();
    }

    @PostMapping("/adjust")
    public SimulationResponse adjust(@Valid @RequestBody ParameterAdjustmentRequest request) {
        throw legacyExecutionGone();
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

    private ApiException legacyExecutionGone() {
        return new ApiException(HttpStatus.GONE,
                "Legacy numerical solver execution has been retired. Use /api/matter-flow for new simulations.",
                "LEGACY_EXECUTION_RETIRED", "MATTER_FLOW");
    }
}
