package com.example.backend.controller.curriculum;

import com.example.backend.dto.curriculum.CurriculumTreeResponse;
import com.example.backend.dto.curriculum.CurriculumTreeResponse.TopicItem;
import com.example.backend.entity.problem.SchemaVersion;
import com.example.backend.service.curriculum.CurriculumService;
import com.example.backend.service.problem.SchemaDefinitionService;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.*;

class CurriculumControllerTest {
    @Test
    void simulationLessonsUseApprovedSchemaTopicAndExcludeInactiveCurriculum() {
        var curriculum = mock(CurriculumService.class);
        var schemas = mock(SchemaDefinitionService.class);
        var schema = new SchemaVersion();
        schema.setTopic("KINEMATICS");
        when(schemas.requireCurrentApproved("thpt_kinematics", "1.0")).thenReturn(schema);
        var motion = new TopicItem(UUID.randomUUID(), "KINEMATICS", "kinematics", true, 0, List.of());
        when(curriculum.getActiveTreeForTopic("KINEMATICS")).thenReturn(new CurriculumTreeResponse(List.of(motion)));

        var result = new CurriculumController(curriculum, schemas).getTree(true, "thpt_kinematics", "1.0");

        assertEquals(List.of(motion), result.topics());
        verify(curriculum).getActiveTreeForTopic("KINEMATICS");
        verify(curriculum, never()).getTree(anyBoolean());
        verify(schemas).requireCurrentApproved("thpt_kinematics", "1.0");
    }

    @Test
    void generalCurriculumRequestKeepsExistingBehaviour() {
        var curriculum = mock(CurriculumService.class);
        var schemas = mock(SchemaDefinitionService.class);
        var tree = new CurriculumTreeResponse(List.of());
        when(curriculum.getTree(true)).thenReturn(tree);

        assertEquals(tree, new CurriculumController(curriculum, schemas).getTree(true, null, null));
        verifyNoInteractions(schemas);
    }
}
