package com.example.backend.bootstrap;

import com.example.backend.physics.module.PhysicsModuleRegistry;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;

import java.io.InputStream;

/** Fails application startup before other catalog runners can seed an invalid approved catalog. */
@Component
@Order(Ordered.HIGHEST_PRECEDENCE)
@RequiredArgsConstructor
public class ApprovedLatestPhysicsModuleBindingAuditRunner implements CommandLineRunner {
    private final ObjectMapper objectMapper;
    private final PhysicsModuleRegistry physicsModules;

    @Override
    public void run(String... args) throws Exception {
        try (InputStream input = new ClassPathResource("schemas/catalog.json").getInputStream()) {
            ApprovedLatestPhysicsModuleBindingAudit.requireTypedBindings(
                    ApprovedLatestPhysicsModuleBindingAudit.fromActiveCatalog(objectMapper.readTree(input)),
                    physicsModules);
        }
    }
}
