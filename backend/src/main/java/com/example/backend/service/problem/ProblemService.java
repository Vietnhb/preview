package com.example.backend.service.problem;

import com.example.backend.service.account.CurrentUserService;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.UUID;
import java.time.Instant;
import java.util.Map;

import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
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
import com.example.backend.ai.ocr.OcrProvider;
import com.example.backend.ai.ocr.OcrResult;
import com.example.backend.physics.validation.EndConditionResolver;
import com.example.backend.repository.problem.ExtractionRunRepository;
import com.example.backend.repository.curriculum.LessonRepository;
import com.example.backend.repository.problem.ProblemSubmissionRepository;
import com.example.backend.repository.problem.SourceAssetRepository;
import com.example.backend.repository.problem.SpecificationRepository;
import com.fasterxml.jackson.databind.ObjectMapper;

import lombok.RequiredArgsConstructor;

@Service
@RequiredArgsConstructor
public class ProblemService {

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

    @Transactional(noRollbackFor = ApiException.class)
    public ProblemResponse extract(UUID id) {
        ProblemSubmission problem = requireOwnedProblem(id);
        if (!StringUtils.hasText(problem.getEditableText())) {
            throw new ApiException(HttpStatus.CONFLICT, "Confirm or enter OCR text before extraction");
        }

        ExtractionRun run = new ExtractionRun();
        run.setSubmission(problem);
        run.setExtractionPath(com.example.backend.entity.enums.ExtractionPath.OPENROUTER);
        run.setProviderName("pending");
        run.setStatus(ExtractionRunStatus.RUNNING);
        extractionRunRepository.save(run);

        try {
            ExtractionResult result = extractionCoordinator.extract(problem.getEditableText());
            applyResult(run, result);
            Specification specification = createSpecification(problem, run, result.document());
            readinessService.ensureRequiredAmbiguities(specification);
            specificationRepository.save(specification);
            problem.setCurrentSpecification(specification);
            problem.setStatus(specification.getConfirmationState() == ConfirmationState.UNRESOLVED
                    ? SubmissionStatus.NEEDS_CONFIRMATION
                    : SubmissionStatus.READY_FOR_VALIDATION);
            return mapper.toResponse(problem);
        } catch (ApiException exception) {
            // Keep a failed extraction from looking active when a precondition such as
            // the school's AI quota rejects the request after the run was created.
            run.setStatus(ExtractionRunStatus.FAILED);
            run.setOutcome(ExtractionOutcome.FAILED);
            run.setErrorMessage("AI extraction failed");
            problem.setStatus(SubmissionStatus.FAILED);
            throw exception;
        } catch (RuntimeException exception) {
            run.setStatus(ExtractionRunStatus.FAILED);
            run.setOutcome(ExtractionOutcome.FAILED);
            run.setErrorMessage("AI extraction failed");
            problem.setStatus(SubmissionStatus.FAILED);
            throw new ApiException(HttpStatus.BAD_GATEWAY,
                    "AI problem understanding failed; specification was not created. Cause: " + safeCause(exception));
        }
    }

    @Transactional
    public ProblemResponse confirm(UUID id, Map<String, String> answers) {
        ProblemSubmission problem = requireOwnedProblem(id);
        Specification specification = problem.getCurrentSpecification();
        if (specification == null) {
            throw new ApiException(HttpStatus.CONFLICT, "Extract a specification before confirming it");
        }
        Map<String, String> safeAnswers = answers == null ? Map.of() : answers;
        for (AmbiguityCase ambiguity : specification.getAmbiguityCases()) if (ambiguity.getStatus() == AmbiguityStatus.OPEN
                && !StringUtils.hasText(safeAnswers.get(ambiguity.getCode()))) {
            throw new ApiException(HttpStatus.CONFLICT, "Answer every ambiguity before confirming");
        }
        try { ambiguityResolutionApplier.applyAll(specification, safeAnswers); }
        catch (ApiException exception) { throw exception; }
        catch (RuntimeException exception) { throw new ApiException(HttpStatus.BAD_GATEWAY,
                "AI ambiguity confirmation failed; no changes were saved. Cause: " + safeCause(exception)); }
        readinessService.ensureRequiredAmbiguities(specification);
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
        validateQuantities(request.quantities());
        if (request.endCondition() != null) {
            var errors = EndConditionResolver.validateNode(request.endCondition(), 10);
            if (!errors.isEmpty()) throw new ApiException(HttpStatus.BAD_REQUEST,
                    "Invalid endCondition: " + String.join("; ", errors));
        }
        specification.setObjects(request.objects().deepCopy());
        specification.setQuantities(request.quantities().deepCopy());
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
        specification.setSchemaVersion(schemaDefinitions.requireApproved(document.schemaId()).getVersion());
        specification.setTopic(document.topic());
        specification.setSchemaId(document.schemaId());
        specification.setConfidence(document.confidence());
        specification.setObjects(objectMapper.valueToTree(document.objects()));
        specification.setQuantities(objectMapper.valueToTree(document.quantities()));
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

    private String safeCause(Throwable failure) {
        Throwable current = failure;
        while (current.getCause() != null) current = current.getCause();
        String message = current.getMessage();
        if (!StringUtils.hasText(message)) return current.getClass().getSimpleName();
        String sanitized = message.replaceAll("(?i)bearer\\s+[^\\s,]+", "Bearer [redacted]");
        return sanitized.substring(0, Math.min(240, sanitized.length()));
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
