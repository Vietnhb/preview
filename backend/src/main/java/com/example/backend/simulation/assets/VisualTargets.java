package com.example.backend.simulation.assets;

import java.util.ArrayList;
import java.util.List;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Reads renderer slots from a declared scene; never selects an asset from a physics schema. */
public final class VisualTargets {
    private VisualTargets() { }

    public record Target(String targetId, String kind, String label, JsonNode binding) { }

    public static List<Target> read(JsonNode visualization) {
        List<Target> targets = new ArrayList<>();
        JsonNode presentation = visualization.path("presentation");
        JsonNode nodes = presentation.path("sceneGraph").path("nodes");
        if (nodes.isArray() && !nodes.isEmpty()) {
            readNodes(nodes, "/presentation/sceneGraph/nodes", targets);
        } else {
            JsonNode actors = presentation.path("actors");
            for (int i = 0; i < actors.size(); i++) {
                JsonNode actor = actors.get(i);
                if (!actor.path("x").isTextual()) continue;
                ObjectNode binding = actor.deepCopy();
                binding.remove(List.of("asset", "assetHint", "effects"));
                targets.add(new Target("/presentation/actors/" + i, "actor",
                        actor.path("id").asText(), binding));
            }
            JsonNode props = presentation.path("props");
            for (int i = 0; i < props.size(); i++) {
                targets.add(new Target("/presentation/props/" + i, "prop", props.get(i).asText(), null));
            }
        }
        return List.copyOf(targets);
    }

    private static void readNodes(JsonNode nodes, String path, List<Target> targets) {
        for (int i = 0; i < nodes.size(); i++) {
            JsonNode node = nodes.get(i);
            String type = node.path("type").asText();
            String pointer = path + "/" + i;
            if ("body".equals(type) || "prop".equals(type)) {
                targets.add(new Target(pointer, "body".equals(type) ? "actor" : "prop",
                        node.path("id").asText(), node.path("transform")));
            }
            if (node.path("children").isArray()) readNodes(node.path("children"), pointer + "/children", targets);
        }
    }

    public static void assign(JsonNode visualization, Target target, String assetId, List<String> effects) {
        JsonNode node = visualization.at(target.targetId());
        if (target.targetId().startsWith("/presentation/actors/")) {
            ObjectNode actor = (ObjectNode) node;
            actor.remove("asset");
            actor.put("assetHint", assetId);
            ArrayNode allowed = actor.putArray("effects");
            for (JsonNode effect : visualization.path("presentation").path("effects")) {
                if (effects.contains(effect.asText())) allowed.add(effect.asText());
            }
        } else if (node.isObject()) {
            ObjectNode properties = ((ObjectNode) node).withObject("/properties");
            properties.remove("asset");
            properties.put("assetHint", assetId);
        } else {
            int separator = target.targetId().lastIndexOf('/');
            ((ArrayNode) visualization.at(target.targetId().substring(0, separator)))
                    .set(Integer.parseInt(target.targetId().substring(separator + 1)),
                            com.fasterxml.jackson.databind.node.TextNode.valueOf(assetId));
        }
    }

    /** Remove unmentioned apparatus after assignments so the original slot indexes stay stable. */
    public static void omit(JsonNode visualization, List<Target> omitted) {
        // Mark by identity before removing anything, including nested or double-digit slots.
        var removed = java.util.Collections.newSetFromMap(new java.util.IdentityHashMap<JsonNode, Boolean>());
        omitted.forEach(target -> removed.add(visualization.at(target.targetId())));
        prune(visualization.path("presentation").path("sceneGraph").path("nodes"), removed);
        prune(visualization.path("presentation").path("props"), removed);
    }

    private static void prune(JsonNode nodes, java.util.Set<JsonNode> removed) {
        if (!(nodes instanceof ArrayNode array)) return;
        for (int i = array.size() - 1; i >= 0; i--) {
            JsonNode node = array.get(i);
            prune(node.path("children"), removed);
            if (removed.contains(node)) {
                if (!node.path("children").isEmpty()) {
                    throw new IllegalArgumentException("Cannot omit an apparatus that owns dependent scene nodes");
                }
                array.remove(i);
            }
        }
    }

    public static void filterEffects(JsonNode visualization, java.util.Map<String, List<String>> supported) {
        if (supported.isEmpty()) return;
        JsonNode effects = visualization.path("presentation").path("effects");
        if (effects instanceof ArrayNode array) {
            for (int i = array.size() - 1; i >= 0; i--) {
                String effect = array.get(i).asText();
                if (supported.values().stream().noneMatch(allowed -> allowed.contains(effect))) array.remove(i);
            }
        }
        filterEffectNodes(visualization.path("presentation").path("sceneGraph").path("nodes"), supported);
    }

    private static void filterEffectNodes(JsonNode nodes, java.util.Map<String, List<String>> supported) {
        if (!(nodes instanceof ArrayNode array)) return;
        for (int i = array.size() - 1; i >= 0; i--) {
            JsonNode node = array.get(i);
            filterEffectNodes(node.path("children"), supported);
            if (!"effect".equals(node.path("type").asText())) continue;
            String actorId = node.path("properties").path("actorId").asText();
            String effect = node.path("properties").path("effect").asText();
            if (!actorId.isBlank() && !supported.containsKey(actorId)) continue;
            boolean allowed = actorId.isBlank()
                    ? supported.values().stream().anyMatch(effects -> effects.contains(effect))
                    : supported.getOrDefault(actorId, List.of()).contains(effect);
            if (!allowed) array.remove(i);
        }
    }
}
