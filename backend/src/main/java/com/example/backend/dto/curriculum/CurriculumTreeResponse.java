package com.example.backend.dto.curriculum;

import java.util.List;
import java.util.UUID;

public record CurriculumTreeResponse(List<TopicItem> topics) {

    public record TopicItem(UUID id, String name, String slug, boolean enabled, int sortOrder,
            List<ModuleItem> modules) {
    }

    public record ModuleItem(UUID id, String name, String slug, boolean active, int sortOrder,
            List<LevelItem> levels) {
    }

    public record LevelItem(UUID id, String name, boolean active, int sortOrder, List<LessonItem> lessons) {
    }

    public record LessonItem(UUID id, String name, String slug, boolean active, int sortOrder) {
    }
}
