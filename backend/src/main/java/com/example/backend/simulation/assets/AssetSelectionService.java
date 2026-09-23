package com.example.backend.simulation.assets;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import com.example.backend.ai.extraction.model.SpecificationDocument;
import com.example.backend.config.properties.AssetSelectionProperties;
import com.example.backend.dto.problem.SpecificationResponse;
import com.example.backend.entity.problem.Specification;
import com.example.backend.exception.ApiException;
import com.example.backend.repository.problem.SpecificationRepository;
import com.example.backend.service.account.CurrentUserService;
import com.example.backend.service.problem.ProblemResponseMapper;
import com.example.backend.service.problem.SchemaDefinitionService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import lombok.RequiredArgsConstructor;

/** Binds initial JEV candidates once and persists visual consent independently of physics. */
@Service
@RequiredArgsConstructor
public class AssetSelectionService {
    private final SvgAssetCatalog catalog;
    private final SchemaDefinitionService schemas;
    private final AssetSelectionProperties properties;
    private final ObjectMapper mapper;
    private final SpecificationRepository specifications;
    private final CurrentUserService users;
    private final ProblemResponseMapper responses;

    public Map<String, Object> promptContext(com.example.backend.schema.routing.model.SchemaRoutingDecision routing) {
        if (routing.assets() == null) return Map.of();
        List<Map<String, Object>> candidates = routing.assets().candidates().stream().map(candidate -> {
            var asset = catalog.require(candidate.assetId());
            return Map.<String, Object>of("assetId", asset.id(), "kind", asset.kind(), "label", asset.label(),
                    "description", asset.description(), "match", candidate.match());
        }).toList();
        Map<String, Object> targets = new java.util.LinkedHashMap<>();
        routing.extractionCandidates().forEach(candidate -> targets.put(candidate.schemaId(), VisualTargets.read(
                schemas.requireCurrentApproved(candidate.schemaId(), candidate.schemaVersion())
                        .getDefinition().path("visualization"))));
        return Map.of("jevAssetCandidates", candidates, "visualTargetsBySchema", targets);
    }

    public JsonNode create(SpecificationDocument document, AssetRoutingDecision routing, String text) {
        if (routing == null) throw new IllegalArgumentException("Initial JEV asset routing is missing");
        if (!catalog.checksum().equals(routing.catalogChecksum())) {
            throw new IllegalArgumentException("Asset catalog changed during extraction");
        }
        JsonNode definition = schemas.requireCurrentApproved(document.schemaId(), document.schemaVersion()).getDefinition();
        JsonNode visualization = schemas.visualization(definition);
        List<VisualTargets.Target> targets = VisualTargets.read(visualization);
        Map<String, VisualBinding> bindings = new HashMap<>();
        document.visualBindings().forEach(binding -> {
            if (bindings.put(binding.targetId(), binding) != null) throw new IllegalArgumentException("Duplicate visual target");
        });
        if (!bindings.keySet().equals(targets.stream().map(VisualTargets.Target::targetId)
                .collect(java.util.stream.Collectors.toSet()))) {
            throw new IllegalArgumentException("Visual bindings must cover the selected renderer targets exactly");
        }
        Map<String, AssetRoutingDecision.Candidate> candidates = new HashMap<>();
        routing.candidates().forEach(candidate -> candidates.put(candidate.assetId(), candidate));
        Map<String, com.example.backend.ai.extraction.model.PhysicalObject> entities = new HashMap<>();
        document.objects().forEach(entity -> entities.put(entity.id(), entity));
        ObjectNode plan = mapper.createObjectNode();
        plan.put("id", UUID.randomUUID().toString());
        plan.put("catalogChecksum", catalog.checksum());
        plan.put("sourceFingerprint", fingerprint(document.schemaId(), document.schemaVersion(), text,
                mapper.valueToTree(document.objects())));
        var choices = plan.putArray("choices");
        boolean pending = false;
        boolean unsupported = false;
        List<VisualTargets.Target> omitted = new ArrayList<>();
        Map<String, List<String>> allowedEffects = new HashMap<>();
        HashSet<String> actorEntities = new HashSet<>();
        for (var target : targets) {
            VisualBinding binding = bindings.get(target.targetId());
            var entity = entities.get(binding.entityId());
            if (binding.entityId() == null && binding.assetId() == null
                    && "OMITTED".equals(binding.match()) && "prop".equals(target.kind())) {
                omitted.add(target);
                continue;
            }
            if (entity == null) throw new IllegalArgumentException("Visual target " + target.targetId()
                    + " references entityId=" + binding.entityId() + " but objects IDs are " + entities.keySet()
                    + ". Include each referenced physical object in objects and copy its exact id into entityId.");
            if ("actor".equals(target.kind()) && !actorEntities.add(entity.id())) {
                throw new IllegalArgumentException("Different moving render slots must reference different entities");
            }
            if (("SUBSTITUTE".equals(binding.match()) || "UNSUPPORTED".equals(binding.match()))
                    && !org.springframework.util.StringUtils.hasText(binding.visualDifference())) {
                throw new IllegalArgumentException("A proposed substitute or unsupported visual needs an AI explanation.");
            }
            ObjectNode choice = choices.addObject();
            choice.put("targetId", target.targetId());
            choice.put("entityId", entity.id());
            choice.put("entityLabel", entity.label());
            if (binding.visualDifference() == null) choice.putNull("visualDifference");
            else choice.put("visualDifference", binding.visualDifference());
            choice.put("assetId", binding.assetId());
            if (binding.assetId() == null) {
                if (!"UNSUPPORTED".equals(binding.match())) throw new IllegalArgumentException("Missing asset must be unsupported");
                choice.putNull("assetLabel");
                choice.put("match", "UNSUPPORTED");
                choice.put("requiresConfirmation", false);
                unsupported = true;
                continue;
            }
            var candidate = candidates.get(binding.assetId());
            var asset = catalog.require(binding.assetId());
            if (candidate == null || !asset.kind().equals(target.kind())) {
                throw new IllegalArgumentException("Visual binding is outside the JEV candidates or renderer capability");
            }
            if (!List.of("EXACT", "SUBSTITUTE").contains(binding.match())) {
                throw new IllegalArgumentException("Selected asset requires an exact or substitute classification");
            }
            boolean confirmation = !"EXACT".equals(candidate.match()) || !"EXACT".equals(binding.match())
                    || candidate.confidence() < properties.minimumConfidence();
            choice.put("assetLabel", asset.label());
            choice.put("match", confirmation ? "SUBSTITUTE" : "EXACT");
            choice.put("requiresConfirmation", confirmation);
            pending |= confirmation;
            VisualTargets.assign(visualization, target, asset.id(), asset.effects());
            allowedEffects.put(target.label(), asset.effects());
        }
        VisualTargets.omit(visualization, omitted);
        VisualTargets.filterEffects(visualization, allowedEffects);
        var represented = document.visualBindings().stream().map(VisualBinding::entityId)
                .filter(java.util.Objects::nonNull).collect(java.util.stream.Collectors.toSet());
        if (!represented.containsAll(entities.keySet())) {
            throw new IllegalArgumentException(
                    "Every physical object needs an explicit JEV-bound visual representation or unsupported decision.");
        }
        plan.put("status", unsupported ? "UNSUPPORTED" : pending ? "NEEDS_CONFIRMATION" : "READY");
        plan.set("bindings", mapper.valueToTree(document.visualBindings()));
        plan.set("visualization", visualization);
        return plan;
    }

    public record Decision(UUID selectionId, Boolean accepted) { }

    @Transactional
    public SpecificationResponse decide(UUID specificationId, Decision decision) {
        if (decision == null || decision.selectionId() == null || decision.accepted() == null) {
            throw new ApiException(HttpStatus.BAD_REQUEST, "Cần gửi lựa chọn hình và quyết định xác nhận.");
        }
        Specification specification = specifications.lockOwned(specificationId, users.requireCurrentUser())
                .orElseThrow(() -> new ApiException(HttpStatus.NOT_FOUND, "Specification not found"));
        JsonNode selection = specification.getAssetSelection();
        if (selection == null
                || !decision.selectionId().toString().equals(selection.path("id").asText())) {
            throw new ApiException(HttpStatus.CONFLICT, "Lựa chọn hình đã thay đổi. Hãy mở lại đề bài.");
        }
        requireCurrent(specification);
        String desiredStatus = decision.accepted() ? "READY" : "REJECTED";
        if (desiredStatus.equals(selection.path("status").asText())) return responses.toSpecification(specification);
        if (!"NEEDS_CONFIRMATION".equals(selection.path("status").asText())) {
            throw new ApiException(HttpStatus.CONFLICT, "Lựa chọn hình này đã được xử lý.");
        }
        ObjectNode updated = selection.deepCopy();
        updated.put("status", desiredStatus);
        updated.put("decidedAt", java.time.Instant.now().toString());
        specification.setAssetSelection(updated);
        return responses.toSpecification(specification);
    }

    public static JsonNode requireReady(Specification specification) {
        requireCurrent(specification);
        JsonNode selection = specification.getAssetSelection();
        if (!"READY".equals(selection.path("status").asText()) || !selection.path("visualization").isObject()) {
            throw new ApiException(HttpStatus.CONFLICT, "Chưa có hình minh họa được chấp nhận để chạy mô phỏng.");
        }
        return selection.path("visualization").deepCopy();
    }

    private static void requireCurrent(Specification specification) {
        JsonNode selection = specification.getAssetSelection();
        if (selection == null || specification.getSubmission() == null) {
            throw new ApiException(HttpStatus.CONFLICT, "Cần phân tích đề bài để chọn hình minh họa trước khi chạy.");
        }
        String current = fingerprint(specification.getSchemaId(), specification.getSchemaVersion(),
                specification.getSubmission().getEditableText(), specification.getObjects());
        if (!current.equals(selection.path("sourceFingerprint").asText())) {
            throw new ApiException(HttpStatus.CONFLICT, "Đề bài hoặc vật thể đã đổi. Cần phân tích lại để chọn hình phù hợp.");
        }
    }

    static String fingerprint(String schema, String version, String text, JsonNode objects) {
        List<String> identities = new ArrayList<>();
        if (objects != null) objects.forEach(object -> identities.add(List.of(object.path("id").asText(),
                object.path("label").asText(), object.path("type").asText()).toString()));
        identities.sort(String::compareTo);
        try {
            String data = List.of(schema, version, text, identities.toString()).toString();
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(data.getBytes(StandardCharsets.UTF_8)));
        } catch (java.security.NoSuchAlgorithmException impossible) {
            throw new IllegalStateException(impossible);
        }
    }
}
