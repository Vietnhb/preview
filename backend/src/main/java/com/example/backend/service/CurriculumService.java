package com.example.backend.service;

import java.util.List;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.example.backend.dto.curriculum.CurriculumTreeResponse;
import com.example.backend.dto.curriculum.CurriculumTreeResponse.LessonItem;
import com.example.backend.dto.curriculum.CurriculumTreeResponse.LevelItem;
import com.example.backend.dto.curriculum.CurriculumTreeResponse.ModuleItem;
import com.example.backend.dto.curriculum.CurriculumTreeResponse.TopicItem;
import com.example.backend.entity.ContentModule;
import com.example.backend.entity.GradeLevel;
import com.example.backend.entity.Lesson;
import com.example.backend.entity.Topic;
import com.example.backend.repository.TopicRepository;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class CurriculumService {

    private final TopicRepository topicRepository;

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
