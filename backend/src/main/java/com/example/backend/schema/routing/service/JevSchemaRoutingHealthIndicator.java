package com.example.backend.schema.routing.service;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;

import com.example.backend.config.properties.JevProperties;
import com.example.backend.config.properties.SchemaRoutingProperties;
import com.example.backend.service.problem.SchemaDefinitionService;

/** Health status for the Jev-only runtime schema route. */
@Component("schemaRouting")
public final class JevSchemaRoutingHealthIndicator implements HealthIndicator {
    private final JevProperties jev;
    private final SchemaRoutingProperties routing;
    private final SchemaDefinitionService schemas;

    public JevSchemaRoutingHealthIndicator(JevProperties jev, SchemaRoutingProperties routing,
            SchemaDefinitionService schemas) {
        this.jev = jev;
        this.routing = routing;
        this.schemas = schemas;
    }

    @Override
    public Health health() {
        if (!routing.enabled()) return Health.outOfService().withDetail("routing", "disabled").build();
        if (jev.apiKey().isBlank()) return Health.down().withDetail("reason", "JEV_API_KEY_missing").build();
        try {
            int count = schemas.approvedSchemas().size();
            if (count == 0) return Health.down().withDetail("reason", "no_approved_schemas").build();
            return Health.up().withDetail("provider", "jev")
                    .withDetail("model", jev.model())
                    .withDetail("approvedSchemas", count)
                    .withDetail("route", "typed-choice").build();
        } catch (RuntimeException failure) {
            return Health.down().withDetail("reason", "approved_schema_catalog_unavailable").build();
        }
    }
}
