package com.example.backend.system.curriculum.service;

import com.example.backend.exception.ApiException;
import com.example.backend.system.curriculum.dto.CurriculumNodeRequest;
import com.example.backend.system.curriculum.dto.CurriculumTreeResponse;
import com.example.backend.system.curriculum.model.entity.ContentModule;
import com.example.backend.system.curriculum.model.entity.GradeLevel;
import com.example.backend.system.curriculum.model.entity.Lesson;
import com.example.backend.system.curriculum.model.entity.Topic;
import com.example.backend.system.curriculum.repository.ContentModuleRepository;
import com.example.backend.system.curriculum.repository.GradeLevelRepository;
import com.example.backend.system.curriculum.repository.LessonRepository;
import com.example.backend.system.curriculum.repository.TopicRepository;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CurriculumAdminService {
    private final TopicRepository topicRepository;
    private final ContentModuleRepository moduleRepository;
    private final GradeLevelRepository levelRepository;
    private final LessonRepository lessonRepository;
    private final CurriculumService curriculumService;

    @Transactional
    public CurriculumTreeResponse createTopic(CurriculumNodeRequest request) {
        Topic topic = new Topic();
        topic.setName(request.name().trim());
        topic.setSlug(slug(request, request.name()));
        topic.setSortOrder(order(request));
        topic.setEnabled(true);
        topicRepository.save(topic);
        return tree();
    }

    @Transactional
    public CurriculumTreeResponse createModule(UUID topicId, CurriculumNodeRequest request) {
        Topic topic = topic(topicId);
        ContentModule module = new ContentModule();
        module.setTopic(topic);
        module.setName(request.name().trim());
        module.setSlug(slug(request, request.name()));
        module.setSortOrder(order(request));
        module.setActive(true);
        moduleRepository.save(module);
        return tree();
    }

    @Transactional
    public CurriculumTreeResponse createLevel(UUID moduleId, CurriculumNodeRequest request) {
        ContentModule module = module(moduleId);
        GradeLevel level = new GradeLevel();
        level.setModule(module);
        level.setName(request.name().trim());
        level.setSortOrder(order(request));
        level.setActive(true);
        levelRepository.save(level);
        return tree();
    }

    @Transactional
    public CurriculumTreeResponse createLesson(UUID levelId, CurriculumNodeRequest request) {
        GradeLevel level = level(levelId);
        Lesson lesson = new Lesson();
        lesson.setLevel(level);
        lesson.setName(request.name().trim());
        lesson.setSlug(slug(request, request.name()));
        lesson.setSortOrder(order(request));
        lesson.setActive(true);
        lessonRepository.save(lesson);
        return tree();
    }

    @Transactional
    public CurriculumTreeResponse toggle(String type, UUID id) {
        switch (type.toLowerCase()) {
            case "topic" -> {
                Topic item = topic(id);
                item.setEnabled(!item.isEnabled());
                topicRepository.save(item);
            }
            case "module" -> {
                ContentModule item = module(id);
                item.setActive(!item.isActive());
                moduleRepository.save(item);
            }
            case "level" -> {
                GradeLevel item = level(id);
                item.setActive(!item.isActive());
                levelRepository.save(item);
            }
            case "lesson" -> {
                Lesson item = lesson(id);
                item.setActive(!item.isActive());
                lessonRepository.save(item);
            }
            default -> throw ApiException.badRequest("Unsupported curriculum node type");
        }
        return tree();
    }

    private CurriculumTreeResponse tree() {
        return curriculumService.getTree(true);
    }

    private static int order(CurriculumNodeRequest request) {
        return request.sortOrder() == null ? 0 : request.sortOrder();
    }

    private static String slug(CurriculumNodeRequest request, String fallback) {
        String value = request.slug() == null || request.slug().isBlank() ? fallback : request.slug();
        return value.trim().toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("(^-)|(-$)", "");
    }

    private Topic topic(UUID id) {
        return topicRepository.findById(id).orElseThrow(missing("Topic"));
    }

    private ContentModule module(UUID id) {
        return moduleRepository.findById(id).orElseThrow(missing("Module"));
    }

    private GradeLevel level(UUID id) {
        return levelRepository.findById(id).orElseThrow(missing("Level"));
    }

    private Lesson lesson(UUID id) {
        return lessonRepository.findById(id).orElseThrow(missing("Lesson"));
    }

    private static java.util.function.Supplier<ApiException> missing(String type) {
        return () -> ApiException.notFound(type + " not found");
    }
}
