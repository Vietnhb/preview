package com.example.backend.controller;

import com.example.backend.dto.physics.ParameterAdjustmentRequest;
import com.example.backend.dto.physics.SimulationResponse;
import com.example.backend.service.SimulationService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.annotation.SendToUser;
import org.springframework.stereotype.Controller;

@Controller
@RequiredArgsConstructor
public class SimulationWebSocketController {
    private final SimulationService simulationService;

    @MessageMapping("/simulation/adjust")
    @SendToUser("/queue/simulation")
    public SimulationResponse adjust(ParameterAdjustmentRequest request) {
        return simulationService.adjust(request);
    }
}
