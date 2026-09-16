package com.example.backend.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import com.example.backend.entity.Adjudication;
import com.example.backend.entity.BenchmarkProblem;
import com.example.backend.entity.EvaluationRun;
import com.example.backend.entity.ExtractionOutcome;
import com.example.backend.entity.ExtractionPath;
import com.example.backend.entity.GoldAnnotation;
import com.example.backend.entity.SchemaVersion;
import com.example.backend.extraction.ExtractionCoordinator;
import com.example.backend.extraction.ExtractionResult;
import com.example.backend.extraction.PhysicalObject;
import com.example.backend.extraction.PhysicalQuantity;
import com.example.backend.extraction.SpecificationDocument;
import com.example.backend.dto.evaluation.EvaluationResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class EvaluationServiceTest {
    private final ObjectMapper objectMapper = new ObjectMapper();
    private com.example.backend.repository.BenchmarkProblemRepository benchmarks;
    private com.example.backend.repository.EvaluationRunRepository runs;
    private ExtractionCoordinator extraction;
    private SchemaDefinitionService schemas;
    private EvaluationService service;

    @BeforeEach
    void setUp() {
        benchmarks = mock(com.example.backend.repository.BenchmarkProblemRepository.class);
        runs = mock(com.example.backend.repository.EvaluationRunRepository.class);
        extraction = mock(ExtractionCoordinator.class);
        schemas = mock(SchemaDefinitionService.class);
        service = new EvaluationService(benchmarks, runs, extraction, objectMapper, schemas);
    }

    @Test
    void reportsConfirmFlowAndSilentDefaultMetrics() throws Exception {
        BenchmarkProblem benchmark = new BenchmarkProblem();
        benchmark.setProblemText("Một vật có vận tốc 10 m/s.");
        benchmark.setTopic("KINEMATICS");
        benchmark.setActive(true);
        JsonNode goldSpecification = objectMapper.readTree("""
                {"quantities":[
                  {"name":"initial_velocity","normalizedValue":10,"normalizedUnit":"m/s"},
                  {"name":"acceleration","normalizedValue":2,"normalizedUnit":"m/s2"}
                ]}
                """);
        Adjudication adjudication = new Adjudication();
        adjudication.setResolvedSpecification(goldSpecification);
        GoldAnnotation firstAnnotation = new GoldAnnotation();
        firstAnnotation.setAnnotatorReference("user:101");
        firstAnnotation.setGoldSpecification(goldSpecification);
        firstAnnotation.setAnnotationStatus("SUBMITTED");
        benchmark.addAnnotation(firstAnnotation);
        GoldAnnotation secondAnnotation = new GoldAnnotation();
        secondAnnotation.setAnnotatorReference("user:202");
        secondAnnotation.setGoldSpecification(goldSpecification);
        secondAnnotation.setAnnotationStatus("SUBMITTED");
        benchmark.addAnnotation(secondAnnotation);
        benchmark.addAdjudication(adjudication);
        when(benchmarks.findAll()).thenReturn(List.of(benchmark));

        SpecificationDocument document = new SpecificationDocument(
                "1.0", "KINEMATICS", "kinematics_1d", List.of(new PhysicalObject("object_1", "Vật thể", "body")),
                List.of(new PhysicalQuantity("initial_velocity", "v0", BigDecimal.TEN, "m/s", BigDecimal.TEN,
                        "m/s", BigDecimal.ONE, "10 m/s")), List.of(), BigDecimal.ONE, List.of());
        when(extraction.extract(benchmark.getProblemText())).thenReturn(new ExtractionResult(document,
                ExtractionPath.RULE_BASED, ExtractionOutcome.RULE_BASED_FALLBACK, "rule-based", "catalog-v1", null, null));

        SchemaVersion schema = new SchemaVersion();
        schema.setDefinition(objectMapper.readTree("""
                {"requiredQuantities":[
                  {"key":"initial_velocity","aliases":["v0"],"allowedUnits":["m/s"]},
                  {"key":"acceleration","aliases":["a"],"allowedUnits":["m/s2"]},
                  {"key":"initial_position","aliases":["x0"],"allowedUnits":["m"]}
                ],"validation":{"tolerance":0.02}}
                """));
        when(schemas.requireApproved("kinematics_1d")).thenReturn(schema);

        EvaluationResponse response = service.run();

        assertThat(response.evaluationType()).isEqualTo("CONFIRM_FLOW_VS_SILENT_DEFAULT");
        assertThat(response.benchmarkCount()).isEqualTo(1);
        assertThat(response.precision()).isEqualTo(1.0);
        assertThat(response.recall()).isEqualTo(0.5);
        JsonNode details = response.details();
        assertThat(details.path("confirmFlow").path("condition").asText()).isEqualTo("CONFIRM_FLOW");
        assertThat(details.path("silentDefaultBaseline").path("condition").asText())
                .isEqualTo("SILENT_DEFAULT_BASELINE");
        assertThat(details.path("byTopic").path("KINEMATICS").path("benchmarkCount").asInt()).isEqualTo(1);
        assertThat(details.path("byQuantityType").path("VELOCITY").path("recall").asDouble()).isEqualTo(1.0);
        assertThat(details.path("numericAgreement").asDouble()).isEqualTo(1.0);
        assertThat(response.kappa()).isEqualTo(1.0);
        verify(runs).save(any(EvaluationRun.class));
    }
}
