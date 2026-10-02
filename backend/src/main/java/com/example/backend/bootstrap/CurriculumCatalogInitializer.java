package com.example.backend.bootstrap;

import com.example.backend.system.curriculum.model.entity.ContentModule;
import com.example.backend.system.curriculum.model.entity.GradeLevel;
import com.example.backend.system.curriculum.model.entity.Lesson;
import com.example.backend.system.curriculum.model.entity.Topic;
import com.example.backend.system.curriculum.repository.ContentModuleRepository;
import com.example.backend.system.curriculum.repository.GradeLevelRepository;
import com.example.backend.system.curriculum.repository.LessonRepository;
import com.example.backend.system.curriculum.repository.TopicRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.IOException;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.CommandLineRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

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
        disableRetiredTopics(catalog);
        for (int topicIndex = 0; topicIndex < catalog.topics().size(); topicIndex++) {
            TopicDefinition definition = catalog.topics().get(topicIndex);
            Topic topic = topicRepository.findBySlug(definition.slug()).orElseGet(Topic::new);
            topic.setName(definition.name()); topic.setSlug(definition.slug()); topic.setEnabled(true); topic.setSortOrder(topicIndex);
            topic = topicRepository.save(topic);
            deactivateGeneratedBootstrap(topic);
            deactivateModulesOutside(topic, definition);
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

    /** Topics listed as retired (strands that cannot be simulated or were split) are hidden, never deleted. */
    private void disableRetiredTopics(CurriculumCatalog catalog) {
        if (catalog.retiredTopicSlugs() == null) return;
        for (String slug : catalog.retiredTopicSlugs()) {
            topicRepository.findBySlug(slug).ifPresent(topic -> {
                topic.setEnabled(false);
                topic.getModules().forEach(module -> module.setActive(false));
                topicRepository.save(topic);
            });
        }
    }

    /** Modules of a catalog topic that are no longer in the catalog are deactivated (saved work keeps its lessons). */
    private void deactivateModulesOutside(Topic topic, TopicDefinition definition) {
        java.util.Set<String> current = new java.util.HashSet<>();
        definition.modules().forEach(module -> current.add(module.slug()));
        for (ContentModule module : topic.getModules()) {
            if (!current.contains(module.getSlug()) && module.isActive()) {
                module.setActive(false);
                moduleRepository.save(module);
            }
        }
    }

    private void deactivateGeneratedBootstrap(Topic topic) {
        moduleRepository.findByTopicAndSlug(topic, "mvp-core").filter(module -> module.getLevels().size() == 1)
                .filter(module -> "THPT".equals(module.getLevels().getFirst().getName()))
                .ifPresent(module -> { module.setActive(false); moduleRepository.save(module); });
    }

    public record CurriculumCatalog(List<TopicDefinition> topics, List<String> retiredTopicSlugs) { }
    public record TopicDefinition(String name, String slug, List<ModuleDefinition> modules) { }
    public record ModuleDefinition(String name, String slug, List<LevelDefinition> levels) { }
    public record LevelDefinition(String name, List<LessonDefinition> lessons) { }
    public record LessonDefinition(String name, String slug) { }
}
