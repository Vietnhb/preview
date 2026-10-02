package com.example.backend.system.physics.dto;

import com.example.backend.base.crud.model.enums.LifecycleStatus;
import com.example.backend.system.physics.model.entity.SolverVersion;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Solver version input and output contracts. */
public final class SolverContracts {
    private SolverContracts() { }
    public record Request(@NotBlank @Size(max = 80) String schemaId, @NotBlank @Size(max = 120) String solverId,
                          @NotBlank @Size(max = 16) String version, @NotNull JsonNode outputDefinition) { }
    public record Response(UUID id, String schemaId, String solverId, String version, JsonNode outputDefinition,
                           String bindingChecksum, LifecycleStatus lifecycleStatus, long recordVersion,
                           Instant createdAt, Instant updatedAt) {
        public static Response from(SolverVersion solver) {
            return new Response(solver.getId(), solver.getSchemaId(), solver.getSolverId(), solver.getVersion(),
                    solver.getOutputDefinition(), solver.getBindingChecksum(), solver.getLifecycleStatus(),
                    solver.getRecordVersion(), solver.getCreatedAt(), solver.getUpdatedAt());
        }
    }
}
