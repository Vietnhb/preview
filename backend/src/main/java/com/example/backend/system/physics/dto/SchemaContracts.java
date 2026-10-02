package com.example.backend.system.physics.dto;

import com.example.backend.base.crud.model.enums.LifecycleStatus;
import com.example.backend.system.physics.model.entity.SchemaVersion;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/** Schema HTTP contracts; entity changes cannot silently expand the public response. */
public final class SchemaContracts {
    private SchemaContracts() { }
    public record Request(@NotBlank @Size(max = 80) String schemaId, @NotBlank @Size(max = 120) String name,
                          @NotBlank @Size(max = 32) String topic, @NotBlank @Size(max = 16) String version,
                          @NotNull JsonNode definition, LifecycleStatus lifecycleStatus) { }
    public record Response(UUID id, String schemaId, String name, String topic, String version,
                           JsonNode definition, String definitionChecksum, LifecycleStatus lifecycleStatus,
                           long recordVersion, Instant createdAt, Instant updatedAt) {
        public static Response from(SchemaVersion schema) {
            return new Response(schema.getId(), schema.getSchemaId(), schema.getName(), schema.getTopic(),
                    schema.getVersion(), schema.getDefinition(), schema.getDefinitionChecksum(),
                    schema.getLifecycleStatus(), schema.getRecordVersion(), schema.getCreatedAt(), schema.getUpdatedAt());
        }
    }
    public record ModuleRelease(UUID id, String topic, String moduleName, String schemaId,
                                String schemaVersion, LifecycleStatus lifecycleStatus) {
        public static ModuleRelease from(SchemaVersion schema) {
            return new ModuleRelease(schema.getId(), schema.getTopic(), schema.getName(), schema.getSchemaId(),
                    schema.getVersion(), schema.getLifecycleStatus());
        }
    }
}
