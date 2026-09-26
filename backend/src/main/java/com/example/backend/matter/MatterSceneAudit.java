package com.example.backend.matter;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.HashSet;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.text.Normalizer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.node.ObjectNode;

/** Review hints for meaning. Executable safety remains with the scene compiler and sandbox. */
public final class MatterSceneAudit {
    private static final Pattern NUMBER = Pattern.compile("(?<![\\p{L}\\p{N}_^])[-+−]?\\d+(?:[.,]\\d+)?(?:[eE][-+]?\\d+)?");
    private static final String REQUIRED_OBJECTS = "requiredObjects";
    private static final String FIXED_QUANTITIES = "fixedQuantities";
    private static final String SOURCE_QUOTE = "sourceQuote";
    private static final String LABEL = "label";
    private MatterSceneAudit() { }

    public static List<String> reviewInventory(String description, JsonNode inventory) {
        List<String> warnings = new ArrayList<>();
        String source = normalize(description);
        Set<String> coveredNumbers = new HashSet<>();
        for (String section : List.of(REQUIRED_OBJECTS, "requiredConstraints",
                "spatialRelations", FIXED_QUANTITIES)) {
            JsonNode entries = inventory.path(section);
            if (!entries.isArray()) {
                warnings.add("The " + section + " inventory is missing; review the interpretation.");
                continue;
            }
            for (JsonNode entry : entries) {
                String cue = normalize(entry.path(SOURCE_QUOTE).asText(""));
                if (cue.isBlank() || !source.contains(cue)) {
                    warnings.add("A " + section + " source cue is missing or paraphrased: "
                            + entry.path(SOURCE_QUOTE).asText("(empty)"));
                }
                if (section.equals(FIXED_QUANTITIES)) coveredNumbers.addAll(numbers(cue));
            }
        }
        if (inventory.path(REQUIRED_OBJECTS).isArray()
                && inventory.path(REQUIRED_OBJECTS).isEmpty()) {
            warnings.add("No simulation object was identified; review the description.");
        }
        for (String number : numbers(source)) {
            if (!coveredNumbers.contains(number)) {
                warnings.add("A source numeral may be missing from the fixed values: " + number);
            }
        }
        return List.copyOf(warnings.stream().distinct().limit(100).toList());
    }

    private static Set<String> numbers(String text) {
        Set<String> values = new HashSet<>();
        Matcher matcher = NUMBER.matcher(text);
        while (matcher.find()) {
            try {
                values.add(new java.math.BigDecimal(matcher.group().replace(',', '.')
                        .replace('−', '-')).stripTrailingZeros().toPlainString());
            } catch (NumberFormatException ignored) { /* The cue remains visible for review. */ }
        }
        return values;
    }

    private static String normalize(String text) {
        return Normalizer.normalize(text, Normalizer.Form.NFKC)
                .replaceAll("\\s+", " ").trim().toLowerCase(java.util.Locale.ROOT);
    }

    public static List<String> review(JsonNode inventory, JsonNode scene) {
        List<String> warnings = new ArrayList<>();
        JsonNode requirements = inventory.path(REQUIRED_OBJECTS);
        JsonNode requiredLinks = inventory.path("requiredConstraints");
        JsonNode bodies = scene.path("bodies");
        JsonNode links = scene.path("constraints");
        if (!requirements.isArray() || !requiredLinks.isArray()
                || !bodies.isArray() || !links.isArray()) {
            return List.of("The requested object inventory could not be compared with the generated scene.");
        }
        Map<Integer, List<JsonNode>> byObject = group(bodies, "sourceObjectIndex", requirements.size(), warnings);
        Map<Integer, List<JsonNode>> byLink = group(links, "sourceConstraintIndex", requiredLinks.size(), warnings);
        for (int index = 0; index < requirements.size(); index++) {
            JsonNode required = requirements.get(index);
            List<JsonNode> actual = byObject.getOrDefault(index, List.of());
            int count = required.path("count").asInt(-1);
            String label = required.path("label").asText("object " + (index + 1));
            if (actual.size() != count) {
                warnings.add("Requested object '" + label + "': expected " + count
                        + ", scene has " + actual.size() + ".");
            }
            String shape = required.path("shape").asText();
            for (JsonNode body : actual) {
                if (!shape.equals("unspecified") && !shape.equals(body.path("shape").asText())) {
                    warnings.add("Requested object '" + label + "' has a different shape in the scene.");
                    break;
                }
            }
            if (actual.size() == 1 && actual.get(0) instanceof ObjectNode mutable) {
                mutable.put(LABEL, label);
            }
        }
        for (int index = 0; index < requiredLinks.size(); index++) {
            int count = requiredLinks.get(index).path("count").asInt(-1);
            int actual = byLink.getOrDefault(index, List.of()).size();
            if (actual != count) warnings.add("Requested link '"
                    + requiredLinks.get(index).path("label").asText("link " + (index + 1))
                    + "': expected " + count + ", scene has " + actual + ".");
        }
        for (JsonNode quantity : inventory.path(FIXED_QUANTITIES)) {
            if (!quantity.isObject()) continue;
            String cue = quantity.path(SOURCE_QUOTE).asText(quantity.path("name").asText("value"));
            JsonNode target = quantity.path("valueSI");
            if (!target.isNumber() || !Double.isFinite(target.doubleValue())) {
                warnings.add("The confirmed value for '" + cue + "' could not be checked.");
                continue;
            }
            List<JsonNode> actualValues = values(quantity, scene, byObject, byLink);
            if (actualValues == null || actualValues.isEmpty()
                    || actualValues.stream().anyMatch(value -> !value.isNumber() && !value.isBoolean())) {
                warnings.add("Check '" + cue + "' against the displayed scene; its value is not automatically verifiable.");
                continue;
            }
            for (JsonNode value : actualValues) {
                double actual;
                if (value.isBoolean()) {
                    actual = value.booleanValue() ? 1 : 0;
                } else if (value.isNumber()) {
                    actual = value.doubleValue();
                } else {
                    actual = Double.NaN;
                }
                if (!Double.isFinite(actual) || !approximatelyEqual(actual, target.doubleValue())) {
                    warnings.add("The scene may not preserve '" + cue + "' ("
                            + target.asText() + " " + quantity.path("unitSI").asText() + ").");
                    break;
                }
            }
        }
        return List.copyOf(warnings.stream().distinct().limit(100).toList());
    }

    private static Map<Integer, List<JsonNode>> group(JsonNode items, String sourceIndex,
            int requirementCount, List<String> warnings) {
        Map<Integer, List<JsonNode>> result = new HashMap<>();
        for (JsonNode item : items) {
            JsonNode index = item.path(sourceIndex);
            if (!index.isIntegralNumber() || index.asInt() < 0 || index.asInt() >= requirementCount) {
                warnings.add("A scene element has no confirmed source object or link.");
                continue;
            }
            result.computeIfAbsent(index.asInt(), ignored -> new ArrayList<>()).add(item);
        }
        return result;
    }

    private static List<JsonNode> values(JsonNode quantity, JsonNode scene,
            Map<Integer, List<JsonNode>> bodies, Map<Integer, List<JsonNode>> links) {
        String field = quantity.path("sceneField").asText("");
        String structure = quantity.path("valueStructure").asText("SCALAR");
        JsonNode bodyScopes = quantity.path("bodyRequirementIndexes");
        JsonNode linkScopes = quantity.path("constraintRequirementIndexes");
        if (!bodyScopes.isArray() || !linkScopes.isArray()) return List.of();
        if (field.equals("DERIVED") || structure.equals("VECTOR_NORM_2D")) return List.of();
        if (field.equals("durationSeconds") && bodyScopes.isEmpty() && linkScopes.isEmpty()) {
            return List.of(scene.path("durationSeconds"));
        }
        if (field.equals("gravity.x") || field.equals("gravity.y")) {
            return bodyScopes.isEmpty() && linkScopes.isEmpty()
                    ? List.of(scene.path("gravity").path(field.substring(8))) : List.of();
        }
        List<JsonNode> values = new ArrayList<>();
        if (field.startsWith("constraint.")) {
            if (!bodyScopes.isEmpty()) return List.of();
            for (JsonNode scope : linkScopes) {
                List<JsonNode> members = links.get(scope.asInt(-1));
                if (members == null || members.isEmpty()) return List.of();
                for (JsonNode member : members) values.add(member.path(field.substring(11)));
            }
            return values;
        }
        if (!linkScopes.isEmpty() || !field.matches("[A-Za-z][A-Za-z0-9]*")) return List.of();
        if (bodyScopes.isEmpty()) {
            if (!field.equals("restitution") && !field.equals("friction")
                    && !field.equals("frictionAir")) return List.of();
            for (List<JsonNode> members : bodies.values()) {
                for (JsonNode member : members) values.add(member.path(field));
            }
            return values;
        }
        for (JsonNode scope : bodyScopes) {
            List<JsonNode> members = bodies.get(scope.asInt(-1));
            if (members == null || members.isEmpty()) return List.of();
            for (JsonNode member : members) values.add(member.path(field));
        }
        return values;
    }

    private static boolean approximatelyEqual(double actual, double expected) {
        return Math.abs(actual - expected) <= 1e-9 * Math.max(1, Math.abs(expected));
    }
}
