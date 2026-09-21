package com.example.backend.service.evaluation;

import com.example.backend.entity.evaluation.Adjudication;
import com.example.backend.entity.evaluation.BenchmarkProblem;
import com.example.backend.entity.evaluation.GoldAnnotation;
import com.example.backend.exception.ApiException;
import com.example.backend.repository.evaluation.BenchmarkProblemRepository;
import com.example.backend.service.account.CurrentUserService;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class BenchmarkReviewService {
    private final BenchmarkProblemRepository repository;
    private final CurrentUserService currentUser;
    private final EntityManager entityManager;

    public record CreateRequest(
            @NotBlank @Size(max = 10000) String problemText,
            @NotBlank @Size(max = 32) String topic,
            @NotBlank @Size(max = 32) String gradeScope,
            @NotBlank @Size(max = 80) String sourceCategory) { }

    public record UpdateRequest(
            @NotBlank @Size(max = 10000) String problemText,
            @NotBlank @Size(max = 32) String topic,
            @NotBlank @Size(max = 32) String gradeScope,
            @NotBlank @Size(max = 80) String sourceCategory) { }

    public record AnnotationRequest(
            @NotNull JsonNode specification,
            @Size(max = 120) String schemaCatalogChecksum,
            @Size(max = 120) String promptVersion,
            @Size(max = 120) String modelVersion) { }

    public record AdjudicationRequest(
            @NotNull JsonNode specification,
            @NotBlank @Size(max = 4000) String rationale,
            @Size(max = 1000) String disagreementCategories) { }

    public record AnnotationView(String actor, JsonNode specification) { }

    public record View(UUID id, String problemText, String topic, String gradeScope, String sourceCategory,
                       String status, long version, String createdByReference, Instant createdAt,
                       int annotationCount, boolean canAnnotate, boolean canAdjudicate,
                       List<AnnotationView> annotations, JsonNode goldSpecification) { }

    public record PageView(List<View> items, int page, int size, long totalElements, int totalPages) { }

    @Transactional(readOnly = true)
    public List<View> list() {
        String actor = actor();
        return repository.findAll(org.springframework.data.domain.Sort.by("createdAt").descending())
                .stream().map(item -> view(item, actor)).toList();
    }

    @Transactional(readOnly = true)
    public PageView page(String status, String topic, String gradeScope, String sourceCategory, Pageable pageable) {
        String actor = actor();
        Page<BenchmarkProblem> result = repository.search(normalize(status), normalize(topic), normalize(gradeScope),
                normalize(sourceCategory), pageable);
        return new PageView(result.getContent().stream().map(item -> view(item, actor)).toList(),
                result.getNumber(), result.getSize(), result.getTotalElements(), result.getTotalPages());
    }

    @Transactional
    public View create(CreateRequest request) {
        String actor = actor();
        BenchmarkProblem benchmark = new BenchmarkProblem();
        benchmark.setProblemText(request.problemText().trim());
        benchmark.setTopic(request.topic().trim());
        benchmark.setGradeScope(request.gradeScope().trim());
        benchmark.setSourceCategory(request.sourceCategory().trim());
        benchmark.setActive(true);
        benchmark.setStatus("DRAFT");
        benchmark.setCreatedByReference(actor);
        return view(repository.save(benchmark), actor);
    }

    @Transactional
    public View updateDraft(UUID id, UpdateRequest request) {
        BenchmarkProblem benchmark = locked(id);
        if (!"DRAFT".equals(benchmark.getStatus()) || !benchmark.getAnnotations().isEmpty()) {
            throw conflict("Only a draft without annotations can be edited");
        }
        benchmark.setProblemText(request.problemText().trim());
        benchmark.setTopic(request.topic().trim());
        benchmark.setGradeScope(request.gradeScope().trim());
        benchmark.setSourceCategory(request.sourceCategory().trim());
        return view(repository.save(benchmark), actor());
    }

    @Transactional
    public View activate(UUID id) {
        BenchmarkProblem benchmark = locked(id);
        if (!"DRAFT".equals(benchmark.getStatus())) throw conflict("Only drafts can be activated");
        benchmark.setStatus("ANNOTATING");
        return view(repository.save(benchmark), actor());
    }

    @Transactional
    public View archive(UUID id, String reason) {
        if (!StringUtils.hasText(reason)) throw new ApiException(HttpStatus.BAD_REQUEST, "Archive reason is required");
        BenchmarkProblem benchmark = locked(id);
        if ("ARCHIVED".equals(benchmark.getStatus())) return view(benchmark, actor());
        benchmark.setStatus("ARCHIVED");
        benchmark.setArchivedReason(reason.trim());
        benchmark.setActive(false);
        return view(repository.save(benchmark), actor());
    }

    @Transactional
    public View annotate(UUID id, AnnotationRequest request) {
        validate(request.specification());
        BenchmarkProblem benchmark = locked(id);
        if (!"ANNOTATING".equals(benchmark.getStatus())) {
            throw conflict("Benchmark must be activated before annotation");
        }
        String actor = actor();
        if (benchmark.getAnnotations().size() >= 2
                || benchmark.getAnnotations().stream().anyMatch(item -> item.getAnnotatorReference().equals(actor))) {
            throw conflict("Two independent annotators are required; an annotation cannot be replaced");
        }
        GoldAnnotation annotation = new GoldAnnotation();
        annotation.setAnnotatorReference(actor);
        annotation.setAnnotationStatus("SUBMITTED");
        annotation.setGoldSpecification(request.specification());
        annotation.setSchemaCatalogChecksum(trimToNull(request.schemaCatalogChecksum()));
        annotation.setPromptVersion(trimToNull(request.promptVersion()));
        annotation.setModelVersion(trimToNull(request.modelVersion()));
        benchmark.addAnnotation(annotation);
        if (benchmark.getAnnotations().size() == 2) {
            benchmark.setStatus(annotationsAgree(benchmark) ? "GOLD_READY" : "DISAGREEMENT");
        }
        return view(repository.save(benchmark), actor);
    }

    @Transactional
    public View adjudicate(UUID id, AdjudicationRequest request) {
        validate(request.specification());
        BenchmarkProblem benchmark = locked(id);
        String actor = actor();
        if (!"DISAGREEMENT".equals(benchmark.getStatus()) || benchmark.getAnnotations().size() != 2
                || !benchmark.getAdjudications().isEmpty()
                || benchmark.getAnnotations().stream().anyMatch(item -> item.getAnnotatorReference().equals(actor))) {
            throw conflict("A third independent reviewer must adjudicate after two disagreeing annotations");
        }
        Adjudication result = new Adjudication();
        result.setReviewerReference(actor);
        result.setResolvedSpecification(request.specification());
        result.setDisagreementState(StringUtils.hasText(request.disagreementCategories())
                ? request.disagreementCategories().trim() : "RESOLVED");
        result.setRationale(request.rationale().trim());
        benchmark.addAdjudication(result);
        benchmark.setStatus("GOLD_READY");
        return view(repository.save(benchmark), actor);
    }

    private BenchmarkProblem locked(UUID id) {
        BenchmarkProblem benchmark = entityManager.find(BenchmarkProblem.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (benchmark == null) throw new ApiException(HttpStatus.NOT_FOUND, "Benchmark not found");
        if (!benchmark.isActive() && !"ARCHIVED".equals(benchmark.getStatus())) {
            throw conflict("Benchmark is inactive");
        }
        return benchmark;
    }

    private String actor() {
        return "user:" + currentUser.requireCurrentUser().getId();
    }

    private String normalize(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private boolean annotationsAgree(BenchmarkProblem benchmark) {
        return benchmark.getAnnotations().size() == 2
                && benchmark.getAnnotations().get(0).getGoldSpecification()
                .equals(benchmark.getAnnotations().get(1).getGoldSpecification());
    }

    private void validate(JsonNode specification) {
        if (specification == null || !specification.isObject()
                || !specification.path("objects").isArray()
                || !specification.path("quantities").isArray()
                || !specification.path("relations").isArray()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Specification must contain objects, quantities and relations arrays");
        }
        if (specification.path("objects").size() > 256
                || specification.path("quantities").size() > 512
                || specification.path("relations").size() > 1024) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Specification contains too many items");
        }
        List<String> names = new ArrayList<>();
        for (JsonNode quantity : specification.path("quantities")) {
            String name = quantity.path("name").asText();
            if (name.isBlank() || !quantity.path("normalizedValue").isNumber()
                    || quantity.path("normalizedUnit").asText().isBlank()) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Each quantity requires name, normalizedValue and normalizedUnit");
            }
            if (!names.add(name.trim().toLowerCase(java.util.Locale.ROOT))) {
                throw new ApiException(HttpStatus.BAD_REQUEST, "Duplicate quantity names are not allowed");
            }
        }
    }

    private View view(BenchmarkProblem benchmark, String actor) {
        String status = StringUtils.hasText(benchmark.getStatus()) ? benchmark.getStatus() : legacyStatus(benchmark);
        boolean complete = benchmark.getAnnotations().size() >= 2;
        boolean own = benchmark.getAnnotations().stream()
                .anyMatch(item -> item.getAnnotatorReference().equals(actor));
        JsonNode gold = benchmark.getAdjudications().stream().findFirst()
                .map(Adjudication::getResolvedSpecification).orElse(null);
        if (gold == null && annotationsAgree(benchmark)) {
            gold = benchmark.getAnnotations().get(0).getGoldSpecification();
        }
        List<AnnotationView> visible = benchmark.getAnnotations().stream()
                .filter(item -> complete || item.getAnnotatorReference().equals(actor))
                .map(item -> new AnnotationView(item.getAnnotatorReference(), item.getGoldSpecification()))
                .toList();
        boolean active = benchmark.isActive() && !"ARCHIVED".equals(status);
        return new View(benchmark.getId(), benchmark.getProblemText(), benchmark.getTopic(), benchmark.getGradeScope(),
                benchmark.getSourceCategory(), status, benchmark.getVersion(), benchmark.getCreatedByReference(),
                benchmark.getCreatedAt(), benchmark.getAnnotations().size(),
                active && "ANNOTATING".equals(status) && benchmark.getAnnotations().size() < 2 && !own,
                active && "DISAGREEMENT".equals(status) && complete && !own && gold == null,
                visible, gold);
    }

    private String legacyStatus(BenchmarkProblem benchmark) {
        if (!benchmark.getAdjudications().isEmpty() || annotationsAgree(benchmark)) return "GOLD_READY";
        return benchmark.getAnnotations().isEmpty() ? "DRAFT" : "ANNOTATING";
    }

    private ApiException conflict(String message) {
        return new ApiException(HttpStatus.CONFLICT, message);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
