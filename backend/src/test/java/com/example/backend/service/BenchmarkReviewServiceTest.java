package com.example.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.util.UUID;

import org.junit.jupiter.api.Test;

import com.example.backend.entity.BenchmarkProblem;
import com.example.backend.entity.User;
import com.example.backend.repository.BenchmarkProblemRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;

class BenchmarkReviewServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void requiresTwoIndependentAnnotatorsBeforeThirdReviewerAdjudicates() throws Exception {
        BenchmarkProblemRepository repository = mock(BenchmarkProblemRepository.class);
        CurrentUserService currentUser = mock(CurrentUserService.class);
        EntityManager entityManager = mock(EntityManager.class);
        BenchmarkReviewService service = new BenchmarkReviewService(repository, currentUser, entityManager);
        BenchmarkProblem benchmark = new BenchmarkProblem();
        benchmark.setId(UUID.randomUUID());
        benchmark.setProblemText("Một vật chuyển động với vận tốc 10 m/s.");
        benchmark.setTopic("KINEMATICS");
        benchmark.setGradeScope("10");
        benchmark.setSourceCategory("test");
        benchmark.setActive(true);
        when(entityManager.find(BenchmarkProblem.class, benchmark.getId(), LockModeType.PESSIMISTIC_WRITE))
                .thenReturn(benchmark);
        when(repository.save(any(BenchmarkProblem.class))).thenAnswer(invocation -> invocation.getArgument(0));

        when(currentUser.requireCurrentUser()).thenReturn(user(101));
        BenchmarkReviewService.View first = service.annotate(benchmark.getId(),
                new BenchmarkReviewService.AnnotationRequest(specification(10)));
        assertThat(first.annotationCount()).isEqualTo(1);
        assertThat(first.canAnnotate()).isFalse();

        when(currentUser.requireCurrentUser()).thenReturn(user(202));
        BenchmarkReviewService.View second = service.annotate(benchmark.getId(),
                new BenchmarkReviewService.AnnotationRequest(specification(12)));
        assertThat(second.annotationCount()).isEqualTo(2);
        assertThat(second.status()).isEqualTo("DISAGREEMENT");

        when(currentUser.requireCurrentUser()).thenReturn(user(303));
        BenchmarkReviewService.View adjudicated = service.adjudicate(benchmark.getId(),
                new BenchmarkReviewService.AnnotationRequest(specification(10)));
        assertThat(adjudicated.status()).isEqualTo("GOLD_READY");
        assertThat(adjudicated.goldSpecification().path("quantities").get(0).path("normalizedValue").asInt())
                .isEqualTo(10);
    }

    private JsonNode specification(int velocity) throws Exception {
        return objectMapper.readTree("""
                {
                  "objects": [],
                  "quantities": [{"name":"velocity","normalizedValue":%d,"normalizedUnit":"m/s"}],
                  "relations": []
                }
                """.formatted(velocity));
    }

    private User user(int id) {
        User user = new User();
        user.setId(id);
        return user;
    }
}
