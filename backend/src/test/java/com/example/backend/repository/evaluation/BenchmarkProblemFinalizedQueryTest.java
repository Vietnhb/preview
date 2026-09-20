package com.example.backend.repository.evaluation;

import com.example.backend.entity.evaluation.Adjudication;
import com.example.backend.entity.evaluation.BenchmarkProblem;
import com.example.backend.entity.evaluation.GoldAnnotation;
import com.example.backend.entity.account.User;
import com.example.backend.entity.school.School;
import com.example.backend.entity.school.SchoolPayment;
import com.example.backend.repository.school.SchoolPaymentRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.beans.factory.annotation.Autowired;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.containers.PostgreSQLContainer;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;

import jakarta.persistence.EntityManager;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

@DataJpaTest(properties = {
        "spring.flyway.enabled=false",
        "spring.jpa.hibernate.ddl-auto=create-drop",
        "spring.sql.init.mode=never"
})
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Testcontainers(disabledWithoutDocker = true)
class BenchmarkProblemFinalizedQueryTest {
    @Container
    private static final PostgreSQLContainer<?> POSTGRES = new PostgreSQLContainer<>("postgres:16-alpine");

    @DynamicPropertySource
    static void dataSourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", POSTGRES::getUsername);
        registry.add("spring.datasource.password", POSTGRES::getPassword);
        registry.add("spring.datasource.driver-class-name", POSTGRES::getDriverClassName);
    }

    @Autowired
    private BenchmarkProblemRepository benchmarks;

    @Autowired
    private EntityManager entityManager;

    @Autowired
    private SchoolPaymentRepository payments;

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void selectsOnlyActiveAdjudicatedOrExactlyAgreedTwoAnnotationProblems() throws Exception {
        BenchmarkProblem adjudicated = problem(true);
        adjudicated.addAdjudication(adjudication(specification("x", 1)));
        persist(adjudicated);

        BenchmarkProblem consensus = problem(true);
        consensus.addAnnotation(annotation("reviewer-a", specification("x", 1)));
        consensus.addAnnotation(annotation("reviewer-b", specification("x", 1)));
        persist(consensus);

        BenchmarkProblem inactive = problem(false);
        inactive.addAdjudication(adjudication(specification("x", 1)));
        persist(inactive);

        BenchmarkProblem oneAnnotation = problem(true);
        oneAnnotation.addAnnotation(annotation("reviewer-a", specification("x", 1)));
        persist(oneAnnotation);

        BenchmarkProblem sameReviewer = problem(true);
        sameReviewer.addAnnotation(annotation("reviewer-a", specification("x", 1)));
        sameReviewer.addAnnotation(annotation("reviewer-a", specification("x", 1)));
        persist(sameReviewer);

        BenchmarkProblem disagreement = problem(true);
        disagreement.addAnnotation(annotation("reviewer-a", specification("x", 1)));
        disagreement.addAnnotation(annotation("reviewer-b", specification("x", 2)));
        persist(disagreement);

        BenchmarkProblem extraAnnotation = problem(true);
        extraAnnotation.addAnnotation(annotation("reviewer-a", specification("x", 1)));
        extraAnnotation.addAnnotation(annotation("reviewer-b", specification("x", 1)));
        extraAnnotation.addAnnotation(annotation("reviewer-c", specification("x", 1)));
        persist(extraAnnotation);

        entityManager.flush();
        entityManager.clear();

        Set<UUID> resultIds = benchmarks.findFinalizedActive().stream()
                .map(BenchmarkProblem::getId)
                .collect(Collectors.toSet());
        assertEquals(Set.of(adjudicated.getId(), consensus.getId()), resultIds);
    }

    @Test
    void aggregatesPaymentStatusCountsAndPaidAmountInTheDatabase() {
        School school = new School();
        school.setName("Aggregation Test School");
        school.setCode("AGGREGATION_TEST");
        entityManager.persist(school);

        User manager = new User();
        manager.setEmail("aggregation-test@example.test");
        manager.setPassword("not-used");
        manager.setFullName("Aggregation Test Manager");
        manager.setSchool(school);
        entityManager.persist(manager);

        payment(school, manager, "PAID", 12_000L);
        payment(school, manager, "PAID", 23_000L);
        payment(school, manager, "PENDING", 40_000L);
        payment(school, manager, "REQUIRES_REVIEW", 50_000L);
        payment(school, manager, "FAILED", 60_000L);
        entityManager.flush();

        SchoolPaymentRepository.RevenueTotals totals = payments.summarizeRevenue();

        assertEquals(2L, totals.getPaidTransactions());
        assertEquals(1L, totals.getPendingTransactions());
        assertEquals(1L, totals.getReviewTransactions());
        assertEquals(35_000L, totals.getGrossPaidVnd());
    }

    private void persist(BenchmarkProblem benchmark) {
        entityManager.persist(benchmark);
    }

    private void payment(School school, User manager, String status, long amount) {
        SchoolPayment payment = new SchoolPayment();
        payment.setSchool(school);
        payment.setManager(manager);
        payment.setPlanCode("test-plan");
        payment.setAmountVnd(amount);
        payment.setStatus(status);
        entityManager.persist(payment);
    }

    private BenchmarkProblem problem(boolean active) {
        BenchmarkProblem benchmark = new BenchmarkProblem();
        benchmark.setProblemText("A test problem");
        benchmark.setTopic("MECHANICS");
        benchmark.setGradeScope("GRADE_10");
        benchmark.setSourceCategory("TEST");
        benchmark.setActive(active);
        return benchmark;
    }

    private GoldAnnotation annotation(String annotator, JsonNode specification) {
        GoldAnnotation annotation = new GoldAnnotation();
        annotation.setAnnotatorReference(annotator);
        annotation.setGoldSpecification(specification);
        annotation.setAnnotationStatus("APPROVED");
        return annotation;
    }

    private Adjudication adjudication(JsonNode specification) {
        Adjudication adjudication = new Adjudication();
        adjudication.setReviewerReference("reviewer");
        adjudication.setResolvedSpecification(specification);
        adjudication.setDisagreementState("RESOLVED");
        return adjudication;
    }

    private JsonNode specification(String key, int value) throws Exception {
        return objectMapper.readTree("{\"quantities\":[{\"name\":\"" + key + "\",\"value\":" + value + "}]}");
    }
}
