package com.example.backend.system.reviewer.dto;

import com.example.backend.system.reviewer.model.entity.Adjudication;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** Benchmark authoring, independent annotation and adjudication contracts. */
public final class BenchmarkContracts {
    private BenchmarkContracts() { }
    public record Draft(@NotBlank @Size(max = 10000) String problemText,
                        @NotBlank @Size(max = 32) String topic, @NotBlank @Size(max = 32) String gradeScope,
                        @NotBlank @Size(max = 80) String sourceCategory) { }
    public record Archive(@NotBlank @Size(max = 4000) String reason) { }
    public record Annotation(@NotNull JsonNode specification, @Size(max = 120) String schemaCatalogChecksum,
                             @Size(max = 120) String promptVersion, @Size(max = 120) String modelVersion) { }
    public record Adjudication(@NotNull JsonNode specification, @NotBlank @Size(max = 4000) String rationale,
                               @Size(max = 1000) String disagreementCategories) { }
    public record AnnotationView(String actor, JsonNode specification) { }
    public record View(UUID id, String problemText, String topic, String gradeScope, String sourceCategory,
                       String status, long version, String createdByReference, Instant createdAt,
                       int annotationCount, boolean canAnnotate, boolean canAdjudicate,
                       List<AnnotationView> annotations, JsonNode goldSpecification) { }
    public record PageView(List<View> items, int page, int size, long totalElements, int totalPages) { }
}
