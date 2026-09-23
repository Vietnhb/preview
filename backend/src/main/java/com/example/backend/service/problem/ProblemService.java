package com.example.backend.service.problem;

import com.example.backend.service.account.CurrentUserService;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.time.Instant;
import java.util.Map;
import java.util.List;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import com.example.backend.dto.problem.CreateProblemRequest;
import com.example.backend.dto.problem.PageResponse;
import com.example.backend.dto.problem.ProblemResponse;
import com.example.backend.dto.problem.ProblemSummaryResponse;
import com.example.backend.dto.problem.UpdateSpecificationRequest;
import com.example.backend.config.properties.UploadProperties;
import com.example.backend.entity.problem.AmbiguityCase;
import com.example.backend.entity.enums.AmbiguityStatus;
import com.example.backend.entity.enums.AssetType;
import com.example.backend.entity.enums.ConfirmationState;
import com.example.backend.entity.enums.ExtractionOutcome;
import com.example.backend.entity.problem.ExtractionRun;
import com.example.backend.entity.enums.ExtractionRunStatus;
import com.example.backend.entity.curriculum.Lesson;
import com.example.backend.entity.enums.OcrStatus;
import com.example.backend.entity.problem.ProblemSubmission;
import com.example.backend.entity.problem.SourceAsset;
import com.example.backend.entity.enums.SourceMode;
import com.example.backend.entity.problem.Specification;
import com.example.backend.entity.enums.SubmissionStatus;
import com.example.backend.entity.account.User;
import com.example.backend.exception.ApiException;
import com.example.backend.ai.extraction.ExtractionCoordinator;
import com.example.backend.ai.extraction.model.AmbiguityItem;
import com.example.backend.ai.extraction.model.ExtractionResult;
import com.example.backend.ai.extraction.model.SpecificationDocument;
import com.example.backend.ai.extraction.model.ConversationTurn;
import com.example.backend.ai.ocr.OcrProvider;
import com.example.backend.ai.ocr.OcrResult;
import com.example.backend.physics.validation.EndConditionResolver;
import com.example.backend.repository.problem.ExtractionRunRepository;
import com.example.backend.repository.curriculum.LessonRepository;
import com.example.backend.repository.problem.ProblemSubmissionRepository;
import com.example.backend.repository.problem.SourceAssetRepository;
import com.example.backend.repository.problem.SpecificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProblemService {
    private static final org.slf4j.Logger log = org.slf4j.LoggerFactory.getLogger(ProblemService.class);

    private final ProblemSubmissionRepository problemRepository;
    private final SourceAssetRepository sourceAssetRepository;
    private final ExtractionRunRepository extractionRunRepository;
    private final SpecificationRepository specificationRepository;
    private final LessonRepository lessonRepository;
    private final CurrentUserService currentUserService;
    private final ProblemResponseMapper mapper;
    private final ExtractionCoordinator extractionCoordinator;
    private final OcrProvider ocrProvider;
    private final ObjectMapper objectMapper;
    private final AmbiguityResolutionApplier ambiguityResolutionApplier;
    private final SpecificationReadinessService readinessService;
    private final SchemaDefinitionService schemaDefinitions;
    private final UploadProperties uploadProperties;
    private final PlatformTransactionManager transactionManager;

    private record ExtractionInput(UUID problemId, UUID runId, Integer ownerId, String text) { }

    @Transactional
    public ProblemResponse create(CreateProblemRequest request) {
        if (request == null || !StringUtils.hasText(request.text())) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Problem text is required");
        }
        SourceMode mode = request.sourceMode() == null ? SourceMode.TEXT : request.sourceMode();
        if (mode != SourceMode.TEXT && mode != SourceMode.PASTE) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Use the image endpoint for IMAGE or MIXED input");
        }

        ProblemSubmission problem = new ProblemSubmission();
        problem.setOwner(currentUserService.requireCurrentUser());
        problem.setLesson(resolveLesson(request.lessonId()));
        problem.setSourceMode(mode);
        problem.setOriginalText(request.text().trim());
        problem.setEditableText(request.text().trim());
        problem.setStatus(SubmissionStatus.DRAFT);
        return mapper.toResponse(problemRepository.save(problem));
    }

    @Transactional
    public ProblemResponse createFromImage(MultipartFile file, String suppliedText, UUID lessonId) {
        validateImage(file);
        User owner = currentUserService.requireCurrentUser();
        byte[] content = readContent(file);
        OcrResult ocr = ocrProvider.recognize(file.getContentType(), content);

        ProblemSubmission problem = new ProblemSubmission();
        problem.setOwner(owner);
        problem.setLesson(resolveLesson(lessonId));
        problem.setSourceMode(StringUtils.hasText(suppliedText) ? SourceMode.MIXED : SourceMode.IMAGE);
        problem.setOriginalText(trimToNull(suppliedText));
        String ocrText = ocr.status() == OcrStatus.SUCCEEDED ? trimToNull(ocr.text()) : null;
        String supplied = trimToNull(suppliedText);
        problem.setEditableText(joinSourceText(supplied, ocrText));
        problem.setStatus(ocr.status() == OcrStatus.SUCCEEDED
                ? SubmissionStatus.OCR_PREVIEW_READY
                : SubmissionStatus.DRAFT);

        SourceAsset asset = new SourceAsset();
        asset.setAssetType(AssetType.IMAGE);
        asset.setOriginalFilename(safeFilename(file.getOriginalFilename()));
        asset.setContentType(file.getContentType());
        asset.setContentLength(content.length);
        asset.setChecksum(sha256(content));
        asset.setContent(content);
        asset.setOcrStatus(ocr.status());
        asset.setOcrText(ocr.text());
        asset.setOcrError(ocr.errorMessage());
        problem.addSourceAsset(asset);

        return mapper.toResponse(problemRepository.save(problem));
    }

    @Transactional(readOnly = true)
    public PageResponse<ProblemSummaryResponse> history(int page, int size) {
        User owner = currentUserService.requireCurrentUser();
        int safePage = Math.max(page, 0);
        int safeSize = Math.clamp(size, 1, 100);
        return PageResponse.from(problemRepository.findByOwnerOrderByCreatedAtDesc(
                owner, PageRequest.of(safePage, safeSize)).map(mapper::toSummary));
    }

    @Transactional(readOnly = true)
    public ProblemResponse get(UUID id) {
        return mapper.toResponse(requireOwnedProblem(id));
    }

    @Transactional
    public ProblemResponse updateText(UUID id, String text) {
        if (!StringUtils.hasText(text)) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Problem text is required");
        }
        ProblemSubmission problem = requireOwnedProblem(id);
        problem.setEditableText(text.trim());
        problem.setStatus(SubmissionStatus.DRAFT);
        return mapper.toResponse(problem);
    }

    public ProblemResponse extract(UUID id) {
        ExtractionInput input = beginExtraction(id);
        try {
            ExtractionResult result = extractionCoordinator.extract(input.text());
            return completeExtraction(input, result);
        } catch (ApiException exception) {
            markExtractionFailed(input);
            throw exception;
        } catch (RuntimeException exception) {
            markExtractionFailed(input);
            log.warn("Problem extraction failed for submission {}", input.problemId(), exception);
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Chưa thể hoàn tất phân tích đề bài. Nội dung chưa được lưu thành spec; bạn có thể thử lại hoặc chỉnh sửa đề.");
        }
    }

    private ExtractionInput beginExtraction(UUID id) {
        return transactionTemplate().execute(status -> {
            ProblemSubmission problem = requireOwnedProblem(id);
            if (!StringUtils.hasText(problem.getEditableText())) {
                throw new ApiException(HttpStatus.CONFLICT, "Confirm or enter OCR text before extraction");
            }

            ExtractionRun run = new ExtractionRun();
            run.setSubmission(problem);
            run.setExtractionPath(com.example.backend.entity.enums.ExtractionPath.AI_PROVIDER);
            run.setProviderName("pending");
            run.setStatus(ExtractionRunStatus.RUNNING);
            extractionRunRepository.saveAndFlush(run);
            return new ExtractionInput(problem.getId(), run.getId(), problem.getOwner().getId(), problem.getEditableText());
        });
    }

    private ProblemResponse completeExtraction(ExtractionInput input, ExtractionResult result) {
        return transactionTemplate().execute(status -> {
            ProblemSubmission problem = problemRepository.findById(input.problemId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Problem not found"));
            if (!input.ownerId().equals(problem.getOwner().getId())
                    || !input.ownerId().equals(currentUserService.requireCurrentUser().getId())) {
                throw new ApiException(HttpStatus.NOT_FOUND, "Problem not found");
            }
            if (!input.text().equals(problem.getEditableText())) {
                throw new ApiException(HttpStatus.CONFLICT, "Đề bài đã được chỉnh sửa trong lúc phân tích. Hãy phân tích lại.");
            }
            ExtractionRun run = extractionRunRepository.findById(input.runId())
                    .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Extraction run not found"));
            if (run.getStatus() != ExtractionRunStatus.RUNNING) {
                throw new ApiException(HttpStatus.CONFLICT, "Extraction run is no longer active");
            }
            applyResult(run, result);
            Specification specification = createSpecification(problem, run, result.document());
            specification.setAssetSelection(result.assetSelection());
            readinessService.ensureRequiredAmbiguities(specification);
            specificationRepository.save(specification);
            problem.setCurrentSpecification(specification);
            problem.setStatus(specification.getConfirmationState() == ConfirmationState.UNRESOLVED
                    ? SubmissionStatus.NEEDS_CONFIRMATION
                    : SubmissionStatus.READY_FOR_VALIDATION);
            return mapper.toResponse(problem);
        });
    }

    private void markExtractionFailed(ExtractionInput input) {
        try {
            transactionTemplate().executeWithoutResult(status -> {
                extractionRunRepository.findById(input.runId()).ifPresent(run -> {
                    run.setStatus(ExtractionRunStatus.FAILED);
                    run.setOutcome(ExtractionOutcome.FAILED);
                    run.setErrorMessage("AI extraction failed");
                    extractionRunRepository.save(run);
                });
                problemRepository.findById(input.problemId()).ifPresent(problem -> {
                    if (input.ownerId().equals(problem.getOwner().getId())) {
                        problem.setStatus(SubmissionStatus.FAILED);
                        problemRepository.save(problem);
                    }
                });
            });
        } catch (RuntimeException ignored) {
            // Preserve the original provider error. The run remains inspectable if the
            // failure transaction itself could not be committed.
        }
    }

    private TransactionTemplate transactionTemplate() {
        return new TransactionTemplate(transactionManager);
    }

    @Transactional
    public ProblemResponse confirm(UUID id, Map<String, String> answers) {
        return confirm(id, new com.example.backend.dto.problem.ConfirmProblemRequest(answers));
    }

    @Transactional
    public ProblemResponse confirm(UUID id, com.example.backend.dto.problem.ConfirmProblemRequest request) {
        ProblemSubmission problem = requireOwnedProblem(id);
        Specification specification = problem.getCurrentSpecification();
        if (specification == null) {
            throw new ApiException(HttpStatus.CONFLICT, "Extract a specification before confirming it");
        }
        Map<String, String> safeAnswers = request == null || request.answers() == null
                ? Map.of() : request.answers();
        List<ConversationTurn> conversation = request == null || request.conversation() == null
                ? List.of() : List.copyOf(request.conversation());
        for (AmbiguityCase ambiguity : specification.getAmbiguityCases()) if (ambiguity.getStatus() == AmbiguityStatus.OPEN
                && !StringUtils.hasText(safeAnswers.get(ambiguity.getCode()))) {
            throw new ApiException(HttpStatus.CONFLICT, "Answer every ambiguity before confirming");
        }
        try { ambiguityResolutionApplier.applyAll(specification, safeAnswers, conversation); }
        catch (ApiException exception) { throw exception; }
        catch (RuntimeException exception) {
            log.warn("Problem clarification failed for submission {}", id, exception);
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "Chưa thể xử lý câu trả lời này. Spec chưa được cập nhật; hãy thử diễn đạt lại.");
        }
        readinessService.ensureRequiredAmbiguities(specification, conversation);
        problem.setStatus(specification.getConfirmationState() == ConfirmationState.UNRESOLVED
                ? SubmissionStatus.NEEDS_CONFIRMATION : SubmissionStatus.READY_FOR_VALIDATION);
        return mapper.toResponse(problem);
    }

    @Transactional
    public ProblemResponse updateSpecification(UUID id, UpdateSpecificationRequest request) {
        if (request == null || request.objects() == null || !request.objects().isArray()
                || request.quantities() == null || !request.quantities().isArray()
                || request.relations() == null || !request.relations().isArray()) {
            throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Specification objects, quantities and relations must be arrays");
        }
        ProblemSubmission problem = requireOwnedProblem(id);
        Specification specification = problem.getCurrentSpecification();
        if (specification == null) {
            throw new ApiException(HttpStatus.CONFLICT, "Extract a specification before editing it");
        }
        var schema = schemaDefinitions.requirePublishedVersion(specification.getSchemaId(), specification.getSchemaVersion());
        var canonicalQuantities = schemaDefinitions.canonicalizeQuantities(
                request.quantities(), schema.getDefinition());
        validateQuantities(canonicalQuantities);
        if (request.endCondition() != null) {
            var errors = EndConditionResolver.validateNode(request.endCondition(), 10);
            if (!errors.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Invalid endCondition: " + String.join("; ", errors));
        }
        specification.setObjects(request.objects().deepCopy());
        specification.setQuantities(canonicalQuantities.deepCopy());
        specification.setRelations(request.relations().deepCopy());
        if (request.endCondition() != null) specification.setEndCondition(request.endCondition().deepCopy());
        specification.setValidationStatus("NOT_VALIDATED");
        specification.setValidationResult(null);
        // Editing the structured facts is a new teacher confirmation. Keep the
        // audit rows, but recompute required gaps from the edited JSON instead
        // of trusting stale AI ambiguity decisions.
        Instant now = Instant.now();
        for (AmbiguityCase ambiguity : specification.getAmbiguityCases()) {
            ambiguity.setStatus(AmbiguityStatus.RESOLVED);
            ambiguity.setResolution("Rechecked after teacher specification edit");
            ambiguity.setResolvedAt(now);
        }
        specification.setConfirmationState(ConfirmationState.CONFIRMED);
        readinessService.ensureRequiredAmbiguities(specification);
        problem.setStatus(specification.getConfirmationState() == ConfirmationState.UNRESOLVED
                ? SubmissionStatus.NEEDS_CONFIRMATION : SubmissionStatus.READY_FOR_VALIDATION);
        return mapper.toResponse(problem);
    }

    @Transactional(readOnly = true)
    public SourceAsset requireOwnedAsset(UUID assetId) {
        User owner = currentUserService.requireCurrentUser();
        return sourceAssetRepository.findByIdAndSubmissionOwner(assetId, owner)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Source asset not found"));
    }

    private Specification createSpecification(ProblemSubmission problem, ExtractionRun run,
            SpecificationDocument document) {
        Specification specification = new Specification();
        specification.setSubmission(problem);
        specification.setExtractionRun(run);
        specification.setContractVersion(SpecificationDocument.CURRENT_SCHEMA_VERSION);
        var schema = schemaDefinitions.requireCurrentApproved(document.schemaId(), document.schemaVersion());
        specification.setSchemaVersion(schema.getVersion());
        specification.setTopic(document.topic());
        specification.setSchemaId(document.schemaId());
        specification.setConfidence(document.confidence());
        specification.setObjects(objectMapper.valueToTree(document.objects()));
        JsonNode extractedQuantities = objectMapper.valueToTree(document.quantities());
        specification.setQuantities(schemaDefinitions.canonicalizeQuantities(
                extractedQuantities, schema.getDefinition()));
        specification.setRelations(objectMapper.valueToTree(document.relations()));
        specification.setEndCondition(document.endCondition());
        specification.setAmbiguity(objectMapper.valueToTree(document.ambiguities()));
        specification.setConfirmationState(document.ambiguities().isEmpty()
                ? ConfirmationState.NO_AMBIGUITY
                : ConfirmationState.UNRESOLVED);
        for (AmbiguityItem item : document.ambiguities()) {
            AmbiguityCase ambiguity = new AmbiguityCase();
            ambiguity.setCode(item.code());
            ambiguity.setFieldPath(item.fieldPath());
            ambiguity.setQuestion(item.question());
            ambiguity.setOptions(objectMapper.valueToTree(item.options()));
            ambiguity.setStatus(AmbiguityStatus.OPEN);
            specification.addAmbiguityCase(ambiguity);
        }
        return specification;
    }

    private void applyResult(ExtractionRun run, ExtractionResult result) {
        run.setExtractionPath(result.path());
        run.setProviderName(result.providerName());
        run.setModelVersion(result.modelVersion());
        run.setStatus(ExtractionRunStatus.SUCCEEDED);
        run.setOutcome(result.outcome());
        run.setRawResponse(result.rawResponse());
        run.setErrorMessage(result.errorMessage());
    }

    private ProblemSubmission requireOwnedProblem(UUID id) {
        User owner = currentUserService.requireCurrentUser();
        return problemRepository.findByIdAndOwner(id, owner)
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Problem not found"));
    }

    private Lesson resolveLesson(UUID lessonId) {
        if (lessonId == null) {
            return null;
        }
        return lessonRepository.findById(lessonId)
                .orElseThrow(() -> new ApiException(HttpStatus.BAD_REQUEST, "Lesson not found"));
    }

    private void validateImage(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Image file is required");
        }
        if (file.getSize() > uploadProperties.maxImageBytes()) {
            throw new ApiException(HttpStatus.PAYLOAD_TOO_LARGE,
                    "Image exceeds the configured upload limit");
        }
        String contentType = file.getContentType() == null ? "" : file.getContentType().toLowerCase(java.util.Locale.ROOT);
        if (!uploadProperties.allowedImageTypes().contains(contentType)) {
            throw new ApiException(HttpStatus.UNSUPPORTED_MEDIA_TYPE, "Unsupported image content type");
        }
    }

    private byte[] readContent(MultipartFile file) {
        try {
            return file.getBytes();
        } catch (Exception exception) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cannot read image file");
        }
    }

    private String safeFilename(String filename) {
        if (!StringUtils.hasText(filename)) {
            return "image";
        }
        String normalized = filename.replace('\\', '/');
        return normalized.substring(normalized.lastIndexOf('/') + 1).replaceAll("[\\r\\n]", "_");
    }

    private String sha256(byte[] content) {
        try {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(content));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is unavailable", exception);
        }
    }

    private String trimToNull(String value) {
        return StringUtils.hasText(value) ? value.trim() : null;
    }

    private String joinSourceText(String first, String second) {
        if (!StringUtils.hasText(first)) return second;
        if (!StringUtils.hasText(second)) return first;
        return first + "\n\n" + second;
    }

    private void validateQuantities(com.fasterxml.jackson.databind.JsonNode quantities) {
        for (com.fasterxml.jackson.databind.JsonNode quantity : quantities) {
            if (!StringUtils.hasText(quantity.path("name").asText())
                    || !quantity.path("normalizedValue").isNumber()
                    || !Double.isFinite(quantity.path("normalizedValue").asDouble())
                    || !StringUtils.hasText(quantity.path("normalizedUnit").asText())) {
                throw new ApiException(HttpStatus.BAD_REQUEST,
                        "Each quantity needs name, finite normalizedValue and normalizedUnit");
            }
        }
    }
}
