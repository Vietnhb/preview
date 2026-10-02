package com.example.backend.bootstrap;

import com.example.backend.system.curriculum.model.entity.Topic;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import com.fasterxml.jackson.databind.node.ObjectNode;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;

/**
 * Compiles the checked-in schema library into topic schema definitions.
 *
 * <pre>
 * schemas/library/defaults.json                     shared policy (vocabulary, visual contract, relations…)
 * schemas/library/quantities.json                   quantity key → Vietnamese label/description/aliases
 * schemas/library/capabilities/&lt;area&gt;/&lt;id&gt;.json   one executable capability (equation AST) per file
 * schemas/topics/grade-NN/&lt;nn&gt;-&lt;slug&gt;.json          topic manifest: curriculum strand + capability ids
 * </pre>
 *
 * A capability is written once and reused by every topic that lists it; the topic definition
 * (quantities, units, laws) is derived from the capabilities, so nothing is duplicated by hand.
 */
public final class SchemaCatalogCompiler {
    public static final String LIBRARY = "schemas/library/";
    public static final String TOPICS = "schemas/topics/";
    private static final JsonNodeFactory NODES = JsonNodeFactory.instance;

    private SchemaCatalogCompiler() { }

    /** Loads and compiles every topic manifest found on the classpath. */
    public static ArrayNode loadFromClasspath(ObjectMapper json) throws IOException {
        PathMatchingResourcePatternResolver resolver = new PathMatchingResourcePatternResolver();
        JsonNode defaults = read(json, resolver.getResource("classpath:" + LIBRARY + "defaults.json"));
        JsonNode quantities = read(json, resolver.getResource("classpath:" + LIBRARY + "quantities.json"));
        List<JsonNode> capabilities = new ArrayList<>();
        for (Resource resource : resolver.getResources("classpath*:" + LIBRARY + "capabilities/**/*.json"))
            capabilities.add(read(json, resource));
        List<JsonNode> topics = new ArrayList<>();
        for (Resource resource : resolver.getResources("classpath*:" + TOPICS + "**/*.json")) {
            ObjectNode topic = (ObjectNode) read(json, resource);
            topic.put("_source", String.valueOf(resource.getFilename()));
            topics.add(topic);
        }
        return compile(defaults, quantities, capabilities, topics);
    }

    /** Pure compilation step (no I/O) so it can be unit-tested with in-memory documents. */
    public static ArrayNode compile(JsonNode defaults, JsonNode quantities, List<JsonNode> capabilities, List<JsonNode> topics) {
        Map<String, JsonNode> library = new LinkedHashMap<>();
        for (JsonNode capability : capabilities) {
            String id = capability.path("capabilityId").asText();
            if (id.isBlank()) throw new IllegalStateException("Capability without capabilityId in schema library");
            if (library.put(id, capability) != null) throw new IllegalStateException("Duplicate capability in schema library: " + id);
        }
        List<JsonNode> ordered = new ArrayList<>(topics);
        ordered.sort(Comparator.comparingInt((JsonNode t) -> t.path("grade").asInt())
                .thenComparing(t -> t.path("_source").asText(t.path("schemaId").asText())));
        ArrayNode catalog = NODES.arrayNode();
        Set<String> seen = new LinkedHashSet<>();
        for (JsonNode topic : ordered) {
            String schemaId = require(topic, "schemaId");
            if (!seen.add(schemaId)) throw new IllegalStateException("Duplicate topic schemaId: " + schemaId);
            ObjectNode entry = catalog.addObject();
            entry.put("schemaId", schemaId);
            entry.put("model", defaults.path("model").asText("generated_visual"));
            entry.put("name", require(topic, "name"));
            entry.put("topic", require(topic, "topic"));
            entry.put("version", require(topic, "version"));
            entry.set("definition", definition(defaults, quantities, library, topic));
        }
        return catalog;
    }

    private static ObjectNode definition(JsonNode defaults, JsonNode quantities, Map<String, JsonNode> library, JsonNode topic) {
        String schemaId = topic.path("schemaId").asText();
        ArrayNode objectTypes = topic.path("objectTypes").deepCopy();
        if (!objectTypes.isArray() || objectTypes.isEmpty())
            throw new IllegalStateException("Topic " + schemaId + " declares no object types");
        ArrayNode typeNames = NODES.arrayNode();
        objectTypes.forEach(type -> typeNames.add(type.path("type").asText()));

        List<JsonNode> capabilities = new ArrayList<>();
        for (JsonNode id : topic.path("capabilities")) {
            JsonNode capability = library.get(id.asText());
            if (capability == null)
                throw new IllegalStateException("Topic " + schemaId + " references unknown capability " + id.asText());
            ObjectNode copy = capability.deepCopy();
            ObjectNode applicability = copy.get("applicability") instanceof ObjectNode existing
                    ? existing : copy.putObject("applicability");
            applicability.set("objectTypes", typeNames.deepCopy());
            capabilities.add(copy);
        }
        if (capabilities.isEmpty()) throw new IllegalStateException("Topic " + schemaId + " lists no capabilities");

        ObjectNode definition = NODES.objectNode();
        definition.put("metaSchemaVersion", defaults.path("metaSchemaVersion").asText("2.0"));
        definition.put("description", require(topic, "description"));
        definition.put("grade", topic.path("grade").asInt());
        definition.set("objectTypes", objectTypes);
        definition.set("relationTypes", relations(defaults, typeNames));
        definition.set("quantityDefinitions", quantityDefinitions(quantities, capabilities, typeNames, schemaId));
        definition.set("unitCatalog", unitCatalog(defaults, capabilities));
        definition.set("laws", laws(capabilities, typeNames));
        definition.set("capabilities", NODES.arrayNode().addAll(capabilities));
        ArrayNode requirements = definition.putArray("applicationRequirements");
        for (JsonNode requirement : defaults.path("applicationRequirements")) {
            ObjectNode item = requirement.deepCopy();
            ObjectNode appliesTo = item.putObject("appliesTo");
            appliesTo.set("objectTypes", typeNames.deepCopy());
            appliesTo.putArray("relationTypes");
            appliesTo.putArray("quantities");
            requirements.add(item);
        }
        for (String field : List.of("coreTypeRefs", "simulationCapability", "limitations", "conceptVocabulary", "visualCapability"))
            if (defaults.has(field)) definition.set(field, defaults.get(field).deepCopy());
        if (topic.has("curriculum")) definition.set("curriculum", topic.get("curriculum").deepCopy());
        return definition;
    }

    private static ArrayNode relations(JsonNode defaults, ArrayNode typeNames) {
        ArrayNode relations = NODES.arrayNode();
        for (JsonNode relation : defaults.path("relationTypes")) {
            ObjectNode item = relation.deepCopy();
            item.set("subjectTypes", typeNames.deepCopy());
            item.set("objectTypes", typeNames.deepCopy());
            item.putObject("appliesTo").set("objectTypes", typeNames.deepCopy());
            relations.add(item);
        }
        return relations;
    }

    private static ArrayNode quantityDefinitions(JsonNode vocabulary, List<JsonNode> capabilities, ArrayNode typeNames, String schemaId) {
        Map<String, Set<String>> units = new LinkedHashMap<>();
        for (JsonNode capability : capabilities)
            for (String section : List.of("canonicalInputs", "outputs"))
                for (JsonNode item : capability.path(section))
                    units.computeIfAbsent(item.path("key").asText(), k -> new LinkedHashSet<>()).add(item.path("unit").asText());
        ArrayNode result = NODES.arrayNode();
        units.forEach((key, allowed) -> {
            JsonNode known = vocabulary.path(key);
            if (!known.hasNonNull("label"))
                throw new IllegalStateException("Quantity '" + key + "' used by " + schemaId + " has no entry in quantities.json");
            ObjectNode quantity = result.addObject();
            quantity.put("key", key);
            quantity.put("label", known.path("label").asText());
            quantity.put("description", known.path("description").asText(known.path("label").asText()));
            quantity.set("aliases", known.path("aliases").isArray() ? known.get("aliases").deepCopy() : NODES.arrayNode());
            ArrayNode unitList = quantity.putArray("allowedUnits");
            allowed.forEach(unitList::add);
            quantity.putObject("appliesTo").set("objectTypes", typeNames.deepCopy());
        });
        return result;
    }

    private static ArrayNode unitCatalog(JsonNode defaults, List<JsonNode> capabilities) {
        Set<String> symbols = new LinkedHashSet<>();
        for (JsonNode capability : capabilities)
            for (String section : List.of("canonicalInputs", "outputs"))
                for (JsonNode item : capability.path(section)) symbols.add(item.path("unit").asText());
        ArrayNode result = NODES.arrayNode();
        for (String symbol : symbols) {
            ObjectNode unit = result.addObject();
            unit.put("symbol", symbol);
            unit.put("dimension", defaults.path("unitDimensions").path(symbol).asText("derived"));
        }
        return result;
    }

    /** One conceptual law per capability, derived from its declared equations and assumptions. */
    private static ArrayNode laws(List<JsonNode> capabilities, ArrayNode typeNames) {
        ArrayNode laws = NODES.arrayNode();
        for (JsonNode capability : capabilities) {
            ObjectNode law = laws.addObject();
            String id = capability.path("capabilityId").asText();
            law.put("id", id);
            law.put("name", capability.path("title").asText(id));
            ArrayNode inputs = law.putArray("inputs");
            capability.path("canonicalInputs").forEach(i -> inputs.add(i.path("key").asText()));
            ArrayNode outputs = law.putArray("outputs");
            capability.path("outputs").forEach(o -> outputs.add(o.path("key").asText()));
            ObjectNode appliesTo = law.putObject("appliesTo");
            appliesTo.set("objectTypes", typeNames.deepCopy());
            ArrayNode quantities = appliesTo.putArray("quantities");
            quantities.addAll(inputs.deepCopy()).addAll(outputs.deepCopy());
            law.set("assumptions", capability.path("assumptions").isArray()
                    ? capability.get("assumptions").deepCopy() : NODES.arrayNode());
            List<String> equations = new ArrayList<>();
            capability.path("equationSet").path("canonical").forEach(e -> equations.add(e.asText()));
            law.put("description", String.join("; ", equations));
        }
        return laws;
    }

    private static String require(JsonNode node, String field) {
        String value = node.path(field).asText("");
        if (value.isBlank()) throw new IllegalStateException("Topic manifest is missing '" + field + "': " + node.path("_source").asText());
        return value;
    }

    private static JsonNode read(ObjectMapper json, Resource resource) throws IOException {
        try (InputStream input = resource.getInputStream()) {
            return json.readTree(input);
        }
    }
}
