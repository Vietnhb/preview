package com.example.backend.bootstrap;

import com.example.backend.Application;
import com.example.backend.system.curriculum.repository.TopicRepository;
import com.example.backend.system.physics.repository.SchemaVersionRepository;
import com.example.backend.system.physics.service.SchemaDefinitionService;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.AnnotationConfigApplicationContext;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.env.MapPropertySource;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.mockito.Mockito.mock;

class PhysicsCatalogBeanRegistrationTest {
    @Test
    void componentScanResolvesCatalogInitializerDependencies() {
        try (var context = new AnnotationConfigApplicationContext()) {
            context.getEnvironment().getPropertySources().addFirst(new MapPropertySource(
                    "bootstrap-test", Map.of("physlive.bootstrap.catalogs-enabled", "true")));
            context.registerBean(SchemaVersionRepository.class, () -> mock(SchemaVersionRepository.class));
            context.registerBean(TopicRepository.class, () -> mock(TopicRepository.class));
            context.registerBean(ObjectMapper.class, () -> new ObjectMapper());

            var scanner = new ClassPathScanningCandidateComponentProvider(true, context.getEnvironment());
            var requiredComponents = Set.of(SchemaDefinitionService.class.getName(),
                    PhysicsCatalogInitializer.class.getName());
            for (var bean : scanner.findCandidateComponents(Application.class.getPackageName())) {
                if (requiredComponents.contains(bean.getBeanClassName())) {
                    context.registerBeanDefinition(bean.getBeanClassName(), bean);
                }
            }

            context.refresh();
            assertNotNull(context.getBean(SchemaDefinitionService.class));
            assertNotNull(context.getBean(PhysicsCatalogInitializer.class));
        }
    }
}
