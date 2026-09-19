package com.example.backend.bootstrap;

import java.io.IOException;
import java.util.List;

import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.example.backend.entity.curriculum.ContentModule;
import com.example.backend.entity.curriculum.GradeLevel;
import com.example.backend.entity.curriculum.Lesson;
import com.example.backend.entity.curriculum.Topic;
import com.example.backend.repository.curriculum.ContentModuleRepository;
import com.example.backend.repository.curriculum.GradeLevelRepository;
import com.example.backend.repository.curriculum.LessonRepository;
import com.example.backend.repository.curriculum.TopicRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Component
@ConditionalOnProperty(prefix = "physlive.bootstrap", name = "catalogs-enabled", havingValue = "true")
@RequiredArgsConstructor
public class CurriculumCatalogInitializer implements CommandLineRunner {
    private static final String CATALOG_PATH = "curriculum/catalog.json";

    private final TopicRepository topicRepository;
    private final ContentModuleRepository moduleRepository;
    private final GradeLevelRepository levelRepository;
    private final LessonRepository lessonRepository;
    private final ObjectMapper objectMapper;

    @Override
    @Transactional
    public void run(String... args) throws IOException {
        CurriculumCatalog catalog = objectMapper.readValue(
                new ClassPathResource(CATALOG_PATH).getInputStream(), CurriculumCatalog.class);
        for (int topicIndex = 0; topicIndex < catalog.topics().size(); topicIndex++) {
            TopicDefinition definition = catalog.topics().get(topicIndex);
            Topic topic = topicRepository.findBySlug(definition.slug()).orElseGet(Topic::new);
            topic.setName(definition.name()); topic.setSlug(definition.slug()); topic.setEnabled(true); topic.setSortOrder(topicIndex);
            topic = topicRepository.save(topic);
            deactivateGeneratedBootstrap(topic);
            for (int moduleIndex = 0; moduleIndex < definition.modules().size(); moduleIndex++) {
                ModuleDefinition moduleDefinition = definition.modules().get(moduleIndex);
                ContentModule module = moduleRepository.findByTopicAndSlug(topic, moduleDefinition.slug()).orElseGet(ContentModule::new);
                module.setTopic(topic); module.setName(moduleDefinition.name()); module.setSlug(moduleDefinition.slug());
                module.setSortOrder(moduleIndex); module.setActive(true); module = moduleRepository.save(module);
                for (int levelIndex = 0; levelIndex < moduleDefinition.levels().size(); levelIndex++) {
                    LevelDefinition levelDefinition = moduleDefinition.levels().get(levelIndex);
                    GradeLevel level = levelRepository.findByModuleAndName(module, levelDefinition.name()).orElseGet(GradeLevel::new);
                    level.setModule(module); level.setName(levelDefinition.name()); level.setSortOrder(levelIndex); level.setActive(true);
                    level = levelRepository.save(level);
                    for (int lessonIndex = 0; lessonIndex < levelDefinition.lessons().size(); lessonIndex++) {
                        LessonDefinition lessonDefinition = levelDefinition.lessons().get(lessonIndex);
                        Lesson lesson = lessonRepository.findByLevelAndSlug(level, lessonDefinition.slug()).orElseGet(Lesson::new);
                        lesson.setLevel(level); lesson.setName(lessonDefinition.name()); lesson.setSlug(lessonDefinition.slug());
                        lesson.setSortOrder(lessonIndex); lesson.setActive(true); lessonRepository.save(lesson);
                    }
                }
            }
        }
    }

    private void deactivateGeneratedBootstrap(Topic topic) {
        moduleRepository.findByTopicAndSlug(topic, "mvp-core").filter(module -> module.getLevels().size() == 1)
                .filter(module -> "THPT".equals(module.getLevels().getFirst().getName()))
                .ifPresent(module -> { module.setActive(false); moduleRepository.save(module); });
    }

    public record CurriculumCatalog(List<TopicDefinition> topics) { }
    public record TopicDefinition(String name, String slug, List<ModuleDefinition> modules) { }
    public record ModuleDefinition(String name, String slug, List<LevelDefinition> levels) { }
    public record LevelDefinition(String name, List<LessonDefinition> lessons) { }
    public record LessonDefinition(String name, String slug) { }
}
