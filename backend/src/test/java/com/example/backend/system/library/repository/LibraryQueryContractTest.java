package com.example.backend.system.library.repository;

import com.example.backend.system.assignment.model.entity.Assignment;
import com.example.backend.system.assignment.repository.AssignmentRepository;
import com.example.backend.system.library.model.entity.LibraryItem;
import jakarta.persistence.Entity;
import org.hibernate.SessionFactory;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.ClassPathScanningCandidateComponentProvider;
import org.springframework.core.type.filter.AnnotationTypeFilter;
import org.springframework.data.jpa.repository.Query;

import static org.junit.jupiter.api.Assertions.*;

/** Validate nullable school joins and UUID string casts against the actual Hibernate model without a database. */
class LibraryQueryContractTest {
    @Test void libraryQueriesCompileWithNullablePlatformSchoolAndStringSchoolParameters() throws Exception {
        var registry = new StandardServiceRegistryBuilder()
            .applySetting("hibernate.dialect", "org.hibernate.dialect.PostgreSQLDialect")
            .applySetting("hibernate.boot.allow_jdbc_metadata_access", false)
            .applySetting("hibernate.hbm2ddl.auto", "none")
            .build();
        try {
            var metadata = new MetadataSources(registry);
            var scanner = new ClassPathScanningCandidateComponentProvider(false); scanner.addIncludeFilter(new AnnotationTypeFilter(Entity.class));
            for (var candidate : scanner.findCandidateComponents("com.example.backend"))
                metadata.addAnnotatedClass(Class.forName(candidate.getBeanClassName()));
            try (SessionFactory factory = metadata.buildMetadata().buildSessionFactory(); var session = factory.openSession()) {
                for (var method : LibraryItemRepository.class.getMethods()) {
                    Query annotation = method.getAnnotation(Query.class);
                    if (annotation == null || annotation.nativeQuery()) continue;
                    var query = session.createQuery(annotation.value(), LibraryItem.class);
                    if (method.getName().equals("findVisiblePublishedSimulation") || method.getName().equals("findSearchVisibleItems")) {
                        assertEquals(String.class, query.getParameter("institutionId").getParameterType());
                        assertTrue(annotation.value().contains("left join i.owner.school ownerSchool"));
                    }
                }
                var assignmentQuery = AssignmentRepository.class.getMethod("findSchoolAssignments", java.util.UUID.class,
                    org.springframework.data.domain.Pageable.class).getAnnotation(Query.class);
                assertEquals(java.util.UUID.class, session.createQuery(assignmentQuery.value(), Assignment.class)
                    .getParameter("schoolId").getParameterType());
                assertTrue(assignmentQuery.value().contains("left join a.schoolClass c"));
            }
        } finally { StandardServiceRegistryBuilder.destroy(registry); }
    }
}
