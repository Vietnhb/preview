package com.example.backend.config;

import org.springframework.boot.CommandLineRunner;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import com.example.backend.repository.SpecificationRepository;
import com.example.backend.service.SchemaDefinitionService;

import lombok.RequiredArgsConstructor;

@Component
@Order(20)
@RequiredArgsConstructor
public class SpecificationSchemaBindingMigration implements CommandLineRunner {
    private final SpecificationRepository specificationRepository;
    private final SchemaDefinitionService schemaDefinitions;

    @Override
    @Transactional
    public void run(String... args) {
        specificationRepository.findAll().stream()
                .filter(specification -> specification.getContractVersion() == null)
                .forEach(specification -> {
                    specification.setContractVersion(specification.getSchemaVersion());
                    if (StringUtils.hasText(specification.getSchemaId())) {
                        specification.setSchemaVersion(
                                schemaDefinitions.requireApproved(specification.getSchemaId()).getVersion());
                    }
                });
    }
}
