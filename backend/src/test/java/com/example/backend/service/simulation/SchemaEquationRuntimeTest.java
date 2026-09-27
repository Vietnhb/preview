package com.example.backend.service.simulation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import static org.junit.jupiter.api.Assertions.*;

class SchemaEquationRuntimeTest {
    private final ObjectMapper json = new ObjectMapper();
    private final SchemaEquationRuntime runtime = new SchemaEquationRuntime(json, 0.02, 300, 16384, 256, 32, 512);

    private JsonNode topic(String name) throws Exception {
        JsonNode catalog = json.readTree(new ClassPathResource("schemas/catalog.json").getInputStream());
        for (JsonNode entry : catalog) if (entry.path("topic").asText().equals(name)) return entry.path("definition");
        throw new IllegalStateException("Topic missing");
    }

    private ObjectNode motion() throws Exception {
        return (ObjectNode) json.readTree("""
            {"durationSeconds":11.5,"physicsCoverage":"COMPLETE",
             "parameters":[{"name":"v0","value":15,"unit":"m/s","min":0,"max":30},
                           {"name":"a","value":2,"unit":"m/s^2","min":-10,"max":10}],
             "physicsModels":[
              {"id":"arbitrary-first","capabilityId":"uniform_acceleration","inputs":{"initial_position":0,"initial_velocity":"v0","acceleration":"a"}},
              {"id":"arbitrary-second","capabilityId":"uniform_acceleration","inputs":{"initial_position":0,"initial_velocity":"v0","acceleration":"a"}}]}
            """);
    }

    @Test void bothParticipantsMatchReferenceAndSlidersRecompute() throws Exception {
        ObjectNode spec = motion();
        JsonNode result = runtime.compute(topic("KINEMATICS"), spec, json.createObjectNode());
        assertEquals("VERIFIED_ANALYTICAL", result.path("validation").path("status").asText());
        JsonNode frames = result.path("solverTimeline").path("frames");
        JsonNode last = frames.get(frames.size() - 1).path("values");
        assertEquals(304.75, last.path("arbitrary-first.position").asDouble(), 1e-8);
        assertEquals(38, last.path("arbitrary-second.velocity").asDouble(), 1e-8);
        JsonNode adjusted = runtime.compute(topic("KINEMATICS"), spec, json.createObjectNode().put("a", 0));
        JsonNode updatedFrames = adjusted.path("solverTimeline").path("frames");
        assertEquals(172.5, updatedFrames.get(updatedFrames.size() - 1).path("values").path("arbitrary-first.position").asDouble(), 1e-8);
    }

    @Test void mutatedReferenceCannotPassVerification() throws Exception {
        JsonNode definition = topic("KINEMATICS").deepCopy();
        for (JsonNode capability : definition.path("capabilities")) {
            if (capability.path("capabilityId").asText().equals("uniform_acceleration")) {
                ObjectNode reference = (ObjectNode) capability.path("execution").path("math").path("closedForm");
                reference.set("position", json.readTree("[\"add\", \"initial_position\", [\"mul\", 99, [\"mul\", \"initial_velocity\", \"t\"]]]"));
            }
        }
        assertEquals("FLAGGED", runtime.compute(definition, motion(), json.createObjectNode()).path("validation").path("status").asText());
    }

    @Test void fiveNumericalOnlyBindingsNeedNoSceneBranch() throws Exception {
        ObjectNode spec = json.createObjectNode().put("durationSeconds", 8).put("physicsCoverage", "COMPLETE");
        spec.putArray("parameters");
        var models = spec.putArray("physicsModels");
        for (int i = 0; i < 5; i++) {
            ObjectNode model = models.addObject().put("id", "item" + i).put("capabilityId", "fixed_length_gravity_oscillation");
            model.putObject("inputs").put("length", 1 + i * 0.2).put("initial_angle", 0.3).put("initial_angular_velocity", 0).put("gravitational_acceleration", 9.8);
        }
        JsonNode result = runtime.compute(topic("DYNAMICS"), spec, json.createObjectNode());
        assertEquals("VERIFIED_NUMERICAL", result.path("validation").path("status").asText());
        assertEquals(5, result.path("validation").path("invariantResults").size());
        assertTrue(result.path("solverTimeline").path("frames").get(1).path("values").has("item4.angle"));
    }

    @Test void clientStatusAndPartialCoverageNeverGrantVerification() throws Exception {
        ObjectNode spec = motion();
        spec.put("physicsCoverage", "PARTIAL");
        spec.putObject("validation").put("status", "VERIFIED_ANALYTICAL");
        assertEquals("VISUAL_ONLY_UNVERIFIED", runtime.compute(topic("KINEMATICS"), spec, json.createObjectNode()).path("validation").path("status").asText());
    }

    @Test void invalidUnitsDomainAndUnknownBindingsAreRejected() throws Exception {
        ObjectNode spec = motion();
        ((ObjectNode) spec.path("parameters").get(0)).put("unit", "km/h");
        assertThrows(RuntimeException.class, () -> runtime.compute(topic("KINEMATICS"), spec, json.createObjectNode()));
        assertThrows(RuntimeException.class, () -> runtime.compute(topic("KINEMATICS"), motion(), json.createObjectNode().put("unrecognized", 1)));
        assertThrows(RuntimeException.class, () -> runtime.compute(topic("KINEMATICS"), motion(), json.createObjectNode().put("a", 20)));
    }

    @Test void circularEquationsCannotReachVerified() throws Exception {
        JsonNode definition = topic("KINEMATICS").deepCopy();
        for (JsonNode capability : definition.path("capabilities")) {
            if (capability.path("capabilityId").asText().equals("uniform_acceleration")) {
                ObjectNode reference = (ObjectNode) capability.path("execution").path("math").path("closedForm");
                reference.put("position", "velocity"); reference.put("velocity", "position");
            }
        }
        assertThrows(RuntimeException.class, () -> runtime.compute(definition, motion(), json.createObjectNode()));
    }
}
