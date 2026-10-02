package com.example.backend.system.library.service;

import com.example.backend.system.library.repository.LibraryItemRepository;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.Executors;
import java.util.stream.IntStream;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import org.springframework.data.jpa.repository.Query;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import static org.junit.jupiter.api.Assertions.*;

/** Exercises the real migration and PostgreSQL conflict handling when Docker is available. */
@Testcontainers(disabledWithoutDocker = true)
class LibraryDiscussionPersistenceTest {
    @Container
    private static final PostgreSQLContainer<?> database = new PostgreSQLContainer<>("postgres:16-alpine");
    private static JdbcTemplate jdbc;

    @BeforeAll
    static void setup() {
        var source = new DriverManagerDataSource(database.getJdbcUrl(), database.getUsername(), database.getPassword());
        jdbc = new JdbcTemplate(source);
        jdbc.execute("create table users(id integer primary key)");
        jdbc.execute("create table library_items(id uuid primary key)");
        new ResourceDatabasePopulator(new ClassPathResource("db/migration/V39__community_discussion.sql")).execute(source);
        jdbc.update("insert into users(id) values (7)");
    }

    @Test
    void repeatedConcurrentLikesKeepOneRowAndRemovingTwiceIsIdempotent() throws Exception {
        UUID itemId = UUID.randomUUID();
        jdbc.update("insert into library_items(id) values (?)", itemId);
        String insert = sql("addLike");
        try (var executor = Executors.newFixedThreadPool(6)) {
            var operations = IntStream.range(0, 12)
                    .<Callable<Integer>>mapToObj(ignored -> () -> jdbc.update(insert, itemId, 7)).toList();
            var results = executor.invokeAll(operations);
            int inserted = 0;
            for (var result : results) inserted += result.get();
            assertEquals(1, inserted);
        }
        assertEquals(1L, jdbc.queryForObject(sql("countLikes"), Long.class, itemId));
        assertEquals(Boolean.TRUE, jdbc.queryForObject(sql("hasLiked"), Boolean.class, itemId, 7));
        assertEquals(1, jdbc.update(sql("removeLike"), itemId, 7));
        assertEquals(0, jdbc.update(sql("removeLike"), itemId, 7));
        assertEquals(0L, jdbc.queryForObject(sql("countLikes"), Long.class, itemId));
    }

    @Test
    void deletingAResourceCascadesItsDiscussionAndReactions() throws Exception {
        UUID itemId = UUID.randomUUID();
        jdbc.update("insert into library_items(id) values (?)", itemId);
        jdbc.update("insert into library_comments(id, item_id, author_id, body) values (?, ?, 7, ?)",
                UUID.randomUUID(), itemId, "Valid comment");
        jdbc.update(sql("addLike"), itemId, 7);
        jdbc.update("delete from library_items where id = ?", itemId);
        assertEquals(0L, jdbc.queryForObject("select count(*) from library_comments where item_id = ?", Long.class, itemId));
        assertEquals(0L, jdbc.queryForObject(sql("countLikes"), Long.class, itemId));
    }

    private String sql(String name) throws Exception {
        Class<?>[] parameters = name.equals("countLikes") ? new Class<?>[]{UUID.class}
                : new Class<?>[]{UUID.class, Integer.class};
        return LibraryItemRepository.class.getMethod(name, parameters).getAnnotation(Query.class).value()
                .replace(":itemId", "?").replace(":userId", "?");
    }
}
