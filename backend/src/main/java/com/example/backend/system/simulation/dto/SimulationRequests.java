package com.example.backend.system.simulation.dto;

import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.util.Map;
import java.util.UUID;

/** Input contracts for understanding, saving and adjusting simulations. */
public final class SimulationRequests {
    private SimulationRequests() { }
    public record Understand(@Size(max = 20_000) String description, String sessionId,
                             @Size(max = 20_000) String recognizedText, @Size(max = 20_000) String correctedText) { }
    public record Save(@NotBlank @Size(max = 160) String title, @NotNull UUID folderId, @NotNull UUID lessonId,
                       @NotNull ObjectNode simulation, @NotNull ObjectNode parameters) { }
    public record Adjustment(UUID simulationId, @NotEmpty Map<String, Double> adjustableParams) { }
}
