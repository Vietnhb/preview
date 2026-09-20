package com.example.backend.schema.routing;

import com.example.backend.entity.curriculum.Topic;
import com.example.backend.repository.account.RoleRepository;
import com.example.backend.repository.account.UserRepository;
import com.example.backend.repository.curriculum.ContentModuleRepository;
import com.example.backend.repository.curriculum.GradeLevelRepository;
import com.example.backend.repository.curriculum.LessonRepository;
import com.example.backend.repository.curriculum.TopicRepository;
import com.example.backend.repository.school.SchoolRepository;
import com.example.backend.repository.simulation.SimulationRunRepository;
import com.example.backend.repository.problem.SchemaVersionRepository;
import com.example.backend.schema.routing.index.SchemaEmbeddingIndexer;
import com.example.backend.service.account.CurrentUserService;
import com.example.backend.service.account.RoleValidationService;
import com.example.backend.service.admin.AdminService;
import com.example.backend.service.curriculum.CurriculumAdminService;
import com.example.backend.service.curriculum.CurriculumService;
import com.example.backend.service.problem.SchemaDefinitionService;
import com.example.backend.service.problem.SchemaService;
import com.example.backend.entity.enums.LifecycleStatus;
import com.example.backend.entity.problem.SchemaVersion;
import com.example.backend.service.school.LicenseCheckService;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

class TopicLifecycleIndexRefreshTest {
    @Test
    void adminTopicToggleRefreshesSearchIndexAfterLifecycleChange() {
        TopicRepository topics = mock(TopicRepository.class);
        SchemaEmbeddingIndexer indexer = mock(SchemaEmbeddingIndexer.class);
        UUID topicId = UUID.randomUUID();
        Topic topic = new Topic();
        topic.setName("Electromagnetism");
        topic.setEnabled(true);
        when(topics.findById(topicId)).thenReturn(Optional.of(topic));

        AdminService admin = new AdminService(mock(UserRepository.class), mock(RoleRepository.class), topics,
                mock(SimulationRunRepository.class), mock(PasswordEncoder.class), mock(CurrentUserService.class),
                mock(RoleValidationService.class), mock(LicenseCheckService.class), mock(SchoolRepository.class),
                mock(EntityManager.class), indexer);

        admin.toggleTopic(topicId);

        verify(topics).save(topic);
        verify(indexer).refreshAfterCatalogChange();
    }

    @Test
    void curriculumTopicToggleRefreshesSearchIndexAfterLifecycleChange() {
        TopicRepository topics = mock(TopicRepository.class);
        SchemaEmbeddingIndexer indexer = mock(SchemaEmbeddingIndexer.class);
        UUID topicId = UUID.randomUUID();
        Topic topic = new Topic();
        topic.setName("Electromagnetism");
        topic.setEnabled(true);
        when(topics.findById(topicId)).thenReturn(Optional.of(topic));
        CurriculumAdminService curriculum = new CurriculumAdminService(topics,
                mock(ContentModuleRepository.class), mock(GradeLevelRepository.class), mock(LessonRepository.class),
                mock(CurriculumService.class), indexer);

        curriculum.toggle("topic", topicId);

        verify(topics).save(topic);
        verify(indexer).refreshAfterCatalogChange();
    }

    @Test
    void schemaRetirementRefreshesTheApprovedIndexAfterLifecycleChange() {
        SchemaVersionRepository schemas = mock(SchemaVersionRepository.class);
        SchemaEmbeddingIndexer indexer = mock(SchemaEmbeddingIndexer.class);
        UUID versionId = UUID.randomUUID();
        SchemaVersion version = new SchemaVersion();
        version.setSchemaId("retired_schema");
        version.setVersion("1.0");
        version.setLifecycleStatus(LifecycleStatus.APPROVED);
        when(schemas.findById(versionId)).thenReturn(Optional.of(version));
        when(schemas.save(version)).thenReturn(version);
        SchemaService service = new SchemaService(schemas, mock(SchemaDefinitionService.class), indexer);

        service.changeVersionLifecycle(versionId, LifecycleStatus.RETIRED);

        verify(indexer).refreshAfterCatalogChange();
    }
}
