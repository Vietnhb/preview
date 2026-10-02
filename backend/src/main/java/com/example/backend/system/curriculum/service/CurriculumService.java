package com.example.backend.system.curriculum.service;

import com.example.backend.system.account.service.CurrentUserService;
import com.example.backend.system.curriculum.dto.CurriculumTreeResponse.LessonItem;
import com.example.backend.system.curriculum.dto.CurriculumTreeResponse.LevelItem;
import com.example.backend.system.curriculum.dto.CurriculumTreeResponse.ModuleItem;
import com.example.backend.system.curriculum.dto.CurriculumTreeResponse.TopicItem;
import com.example.backend.system.curriculum.dto.CurriculumTreeResponse;
import com.example.backend.system.curriculum.model.entity.ContentModule;
import com.example.backend.system.curriculum.model.entity.GradeLevel;
import com.example.backend.system.curriculum.model.entity.Lesson;
import com.example.backend.system.curriculum.model.entity.Topic;
import com.example.backend.system.curriculum.repository.TopicRepository;
import com.example.backend.system.physics.service.SchemaDefinitionService;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CurriculumService {

    private final TopicRepository topicRepository;
    private final SchemaDefinitionService schemas;
    private final CurrentUserService currentUser;

    @Transactional(readOnly = true)
    public CurriculumTreeResponse getTree(boolean includeInactive, String schemaId, String schemaVersion) {
        if (schemaId == null || schemaId.isBlank()) {
            return getTree(includeInactive && currentUser.currentUserOrNull() != null);
        }
        // The approved schema is authoritative, as in the simulation save path.
        return getActiveTreeForTopic(schemas.requireCurrentApproved(schemaId, schemaVersion).getTopic());
    }

    @Transactional(readOnly = true)
    public CurriculumTreeResponse getActiveTreeForTopic(String topic) {
        return new CurriculumTreeResponse(topicRepository.findByNameIgnoreCaseAndEnabledTrueOrderBySortOrderAsc(topic)
                .stream().map(item -> toTopic(item, false)).toList());
    }

    @Transactional(readOnly = true)
    public CurriculumTreeResponse getTree(boolean includeInactive) {
        List<TopicItem> topics = topicRepository.findAllByOrderBySortOrderAsc().stream()
                .filter(topic -> includeInactive || topic.isEnabled())
                .map(topic -> toTopic(topic, includeInactive))
                .toList();
        return new CurriculumTreeResponse(topics);
    }

    private TopicItem toTopic(Topic topic, boolean includeInactive) {
        return new TopicItem(
                topic.getId(),
                topic.getName(),
                topic.getSlug(),
                topic.isEnabled(),
                topic.getSortOrder(),
                topic.getModules().stream()
                        .filter(module -> includeInactive || module.isActive())
                        .map(module -> toModule(module, includeInactive))
                        .toList());
    }

    private ModuleItem toModule(ContentModule module, boolean includeInactive) {
        return new ModuleItem(
                module.getId(),
                module.getName(),
                module.getSlug(),
                module.isActive(),
                module.getSortOrder(),
                module.getLevels().stream()
                        .filter(level -> includeInactive || level.isActive())
                        .map(level -> toLevel(level, includeInactive))
                        .toList());
    }

    private LevelItem toLevel(GradeLevel level, boolean includeInactive) {
        return new LevelItem(
                level.getId(),
                level.getName(),
                level.isActive(),
                level.getSortOrder(),
                level.getLessons().stream()
                        .filter(lesson -> includeInactive || lesson.isActive())
                        .map(this::toLesson)
                        .toList());
    }

    private LessonItem toLesson(Lesson lesson) {
        return new LessonItem(
                lesson.getId(),
                lesson.getName(),
                lesson.getSlug(),
                lesson.isActive(),
                lesson.getSortOrder());
    }
}
