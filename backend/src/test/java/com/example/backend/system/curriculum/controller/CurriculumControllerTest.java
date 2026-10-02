package com.example.backend.system.curriculum.controller;

import com.example.backend.system.account.model.entity.User;
import com.example.backend.system.account.service.CurrentUserService;
import com.example.backend.system.curriculum.dto.CurriculumTreeResponse;
import com.example.backend.system.curriculum.model.entity.ContentModule;
import com.example.backend.system.curriculum.model.entity.Topic;
import com.example.backend.system.curriculum.repository.TopicRepository;
import com.example.backend.system.curriculum.service.CurriculumService;
import com.example.backend.system.physics.model.entity.SchemaVersion;
import com.example.backend.system.physics.service.SchemaDefinitionService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class CurriculumControllerTest {
    private final TopicRepository topics = mock(TopicRepository.class);
    private final SchemaDefinitionService schemas = mock(SchemaDefinitionService.class);
    private final CurrentUserService current = mock(CurrentUserService.class);
    private final CurriculumController controller = new CurriculumController(new CurriculumService(topics, schemas, current));

    @Test
    void simulationLessonsUseApprovedSchemaTopicAndExcludeInactiveCurriculum() {
        var schema = new SchemaVersion();
        schema.setTopic("KINEMATICS");
        when(schemas.requireCurrentApproved("thpt_kinematics", "1.0")).thenReturn(schema);
        var motion = topic("KINEMATICS", true);
        var activeModule = new ContentModule(); activeModule.setActive(true);
        var inactiveModule = new ContentModule(); inactiveModule.setActive(false);
        motion.getModules().addAll(List.of(activeModule, inactiveModule));
        when(topics.findByNameIgnoreCaseAndEnabledTrueOrderBySortOrderAsc("KINEMATICS")).thenReturn(List.of(motion));

        var result = controller.getTree(true, "thpt_kinematics", "1.0");
        assertEquals(motion.getId(), result.topics().getFirst().id());
        assertEquals(1, result.topics().getFirst().modules().size());
        verify(topics, never()).findAllByOrderBySortOrderAsc();
    }

    @Test
    void authenticatedGeneralRequestCanIncludeInactiveCurriculum() {
        when(current.currentUserOrNull()).thenReturn(new User());
        when(topics.findAllByOrderBySortOrderAsc()).thenReturn(List.of(topic("ACTIVE", true), topic("INACTIVE", false)));
        assertEquals(2, controller.getTree(true, null, null).topics().size());
        verifyNoInteractions(schemas);
    }

    @Test
    void anonymousRequestCannotIncludeInactiveCurriculum() {
        var active = topic("ACTIVE", true);
        when(topics.findAllByOrderBySortOrderAsc()).thenReturn(List.of(active, topic("INACTIVE", false)));
        var result = controller.getTree(true, null, null);
        assertEquals(List.of(active.getId()), result.topics().stream().map(CurriculumTreeResponse.TopicItem::id).toList());
        verifyNoInteractions(schemas);
    }

    private Topic topic(String name, boolean enabled) {
        var topic = new Topic(); topic.setId(UUID.randomUUID()); topic.setName(name); topic.setEnabled(enabled);
        return topic;
    }
}
