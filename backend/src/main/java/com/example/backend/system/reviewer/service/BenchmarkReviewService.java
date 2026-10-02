package com.example.backend.system.reviewer.service;

import com.example.backend.exception.ApiException;
import com.example.backend.system.account.service.CurrentUserService;
import com.example.backend.system.problem.model.entity.Specification;
import com.example.backend.system.reviewer.dto.BenchmarkContracts.*;
import com.example.backend.system.reviewer.dto.BenchmarkContracts;
import com.example.backend.system.reviewer.model.entity.Adjudication;
import com.example.backend.system.reviewer.model.entity.BenchmarkProblem;
import com.example.backend.system.reviewer.model.entity.GoldAnnotation;
import com.example.backend.system.reviewer.repository.BenchmarkProblemRepository;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.persistence.EntityManager;
import jakarta.persistence.LockModeType;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

@Service
@RequiredArgsConstructor
public class BenchmarkReviewService {
    private static final String DRAFT = "DRAFT";
    private static final String ANNOTATING = "ANNOTATING";
    private static final String ARCHIVED = "ARCHIVED";
    private static final String GOLD_READY = "GOLD_READY";
    private static final String DISAGREEMENT = "DISAGREEMENT";
    private static final String QUANTITIES = "quantities";
    private final BenchmarkProblemRepository repository;
    private final CurrentUserService currentUser;
    private final EntityManager entityManager;

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
    public View create(BenchmarkContracts.Draft request) {
        String actor = actor();
        BenchmarkProblem benchmark = new BenchmarkProblem();
        benchmark.setProblemText(request.problemText().trim());
        benchmark.setTopic(request.topic().trim());
        benchmark.setGradeScope(request.gradeScope().trim());
        benchmark.setSourceCategory(request.sourceCategory().trim());
        benchmark.setActive(true);
        benchmark.setStatus(DRAFT);
        benchmark.setCreatedByReference(actor);
        return view(repository.save(benchmark), actor);
    }

    @Transactional
    public View updateDraft(UUID id, BenchmarkContracts.Draft request) {
        BenchmarkProblem benchmark = locked(id);
        if (!DRAFT.equals(benchmark.getStatus()) || !benchmark.getAnnotations().isEmpty()) {
            throw conflict("Chỉ sửa được đề ở dạng bản nháp và chưa có ai gán đáp án.");
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
        if (!DRAFT.equals(benchmark.getStatus())) throw conflict("Chỉ mở được đề đang ở dạng bản nháp.");
        benchmark.setStatus(ANNOTATING);
        return view(repository.save(benchmark), actor());
    }

    @Transactional
    public View archive(UUID id, String reason) {
        if (!StringUtils.hasText(reason)) throw ApiException.badRequest("Vui lòng nhập lý do lưu trữ.");
        BenchmarkProblem benchmark = locked(id);
        if (ARCHIVED.equals(benchmark.getStatus())) return view(benchmark, actor());
        benchmark.setStatus(ARCHIVED);
        benchmark.setArchivedReason(reason.trim());
        benchmark.setActive(false);
        return view(repository.save(benchmark), actor());
    }

    @Transactional
    public View annotate(UUID id, BenchmarkContracts.Annotation request) {
        validate(request.specification());
        BenchmarkProblem benchmark = locked(id);
        if (!ANNOTATING.equals(benchmark.getStatus())) {
            throw conflict("Đề cần được mở cho gán đáp án trước.");
        }
        String actor = actor();
        if (benchmark.getAnnotations().size() >= 2
                || benchmark.getAnnotations().stream().anyMatch(item -> item.getAnnotatorReference().equals(actor))) {
            throw conflict("Bạn đã gán đáp án cho đề này hoặc đề đã đủ 2 người gán.");
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
            benchmark.setStatus(annotationsAgree(benchmark) ? GOLD_READY : DISAGREEMENT);
        }
        return view(repository.save(benchmark), actor);
    }

    @Transactional
    public View adjudicate(UUID id, BenchmarkContracts.Adjudication request) {
        validate(request.specification());
        BenchmarkProblem benchmark = locked(id);
        String actor = actor();
        if (!DISAGREEMENT.equals(benchmark.getStatus()) || benchmark.getAnnotations().size() != 2
                || !benchmark.getAdjudications().isEmpty()
                || benchmark.getAnnotations().stream().anyMatch(item -> item.getAnnotatorReference().equals(actor))) {
            throw conflict("Người phân xử phải là chuyên gia thứ ba, khác hai người đã gán đáp án.");
        }
        Adjudication result = new Adjudication();
        result.setReviewerReference(actor);
        result.setResolvedSpecification(request.specification());
        result.setDisagreementState(StringUtils.hasText(request.disagreementCategories())
                ? request.disagreementCategories().trim() : "RESOLVED");
        result.setRationale(request.rationale().trim());
        benchmark.addAdjudication(result);
        benchmark.setStatus(GOLD_READY);
        return view(repository.save(benchmark), actor);
    }

    private BenchmarkProblem locked(UUID id) {
        BenchmarkProblem benchmark = entityManager.find(BenchmarkProblem.class, id, LockModeType.PESSIMISTIC_WRITE);
        if (benchmark == null) throw ApiException.notFound("Không tìm thấy đề kiểm thử.");
        if (!benchmark.isActive() && !ARCHIVED.equals(benchmark.getStatus())) {
            throw conflict("Đề này đã bị lưu trữ.");
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
                && canonical(benchmark.getAnnotations().get(0).getGoldSpecification())
                .equals(canonical(benchmark.getAnnotations().get(1).getGoldSpecification()));
    }

    /** Row order and blank cells are presentation details; two experts agree when the rows match as sets. */
    private static java.util.Map<String, java.util.List<String>> canonical(JsonNode specification) {
        java.util.Map<String, java.util.List<String>> sections = new java.util.TreeMap<>();
        for (String section : List.of("objects", QUANTITIES, "relations")) {
            List<String> rows = new ArrayList<>();
            for (JsonNode row : specification == null ? com.fasterxml.jackson.databind.node.MissingNode.getInstance() : specification.path(section)) {
                java.util.Map<String, String> cells = new java.util.TreeMap<>();
                row.fields().forEachRemaining(entry -> {
                    JsonNode value = entry.getValue();
                    if (value == null || value.isNull() || value.isTextual() && value.asText().isBlank()) return;
                    if (List.of("confidence", "sourceText", "value", "originalValue", "originalUnit").contains(entry.getKey())) return;
                    cells.put(entry.getKey(), value.isNumber() ? java.math.BigDecimal.valueOf(value.asDouble()).stripTrailingZeros().toPlainString()
                            : value.isTextual() ? value.asText().trim().toLowerCase(java.util.Locale.ROOT) : value.toString());
                });
                if (!cells.isEmpty()) rows.add(cells.toString());
            }
            java.util.Collections.sort(rows);
            sections.put(section, rows);
        }
        return sections;
    }

    private void validate(JsonNode specification) {
        if (specification == null || !specification.isObject()
                || !specification.path("objects").isArray()
                || !specification.path(QUANTITIES).isArray()
                || !specification.path("relations").isArray()) {
            throw ApiException.badRequest("Đáp án phải gồm ba bảng: đối tượng, đại lượng và quan hệ.");
        }
        if (specification.path("objects").size() > 256
                || specification.path(QUANTITIES).size() > 512
                || specification.path("relations").size() > 1024) {
            throw ApiException.badRequest("Đáp án có quá nhiều dòng.");
        }
        List<String> names = new ArrayList<>();
        for (JsonNode quantity : specification.path(QUANTITIES)) {
            String name = quantity.path("name").asText();
            if (name.isBlank() || !quantity.path("normalizedValue").isNumber()
                    || quantity.path("normalizedUnit").asText().isBlank()) {
                throw ApiException.badRequest("Mỗi đại lượng cần có tên, giá trị (số) và đơn vị SI.");
            }
            if (!names.add(name.trim().toLowerCase(java.util.Locale.ROOT))) {
                throw ApiException.badRequest("Có đại lượng bị trùng tên.");
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
        boolean active = benchmark.isActive() && !ARCHIVED.equals(status);
        return new View(benchmark.getId(), benchmark.getProblemText(), benchmark.getTopic(), benchmark.getGradeScope(),
                benchmark.getSourceCategory(), status, benchmark.getVersion(), benchmark.getCreatedByReference(),
                benchmark.getCreatedAt(), benchmark.getAnnotations().size(),
                active && ANNOTATING.equals(status) && benchmark.getAnnotations().size() < 2 && !own,
                active && DISAGREEMENT.equals(status) && complete && !own && gold == null,
                visible, gold);
    }

    private String legacyStatus(BenchmarkProblem benchmark) {
        if (!benchmark.getAdjudications().isEmpty() || annotationsAgree(benchmark)) return GOLD_READY;
        return benchmark.getAnnotations().isEmpty() ? DRAFT : ANNOTATING;
    }

    private ApiException conflict(String message) {
        return ApiException.conflict(message);
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }
}
