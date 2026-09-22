package com.example.backend.ai.extraction.prompt;

import com.example.backend.ai.extraction.prompt.CandidateContractProjection.QuantityProjection;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ExtractionPromptBuilderTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void projectsOnlyCandidateExtractionContractAndOmitsImplementationDetails() throws Exception {
        JsonNode definition = objectMapper.readTree("""
                {
                  "model":"motion_model",
                  "solverId":"secret_solver_id",
                  "referenceSolverId":"secret_reference_id",
                  "requiredQuantities":[
                    {"key":"mass","aliases":["m"],"allowedUnits":["kg"],"positive":true,"symbol":"m"}
                  ],
                  "optionalQuantities":[
                    {"key":"gravity","aliases":["g"],"allowedUnits":["m/s2"],"defaultValue":9.81,
                     "integer":false,"sameUnitAs":"length"}
                  ],
                  "adjustableParameters":[{"key":"mass","symbol":"m"}],
                  "relationTypes":["contact"],
                  "endConditionCapabilities":["time_limit","threshold"],
                  "formula":"SECRET_FORMULA",
                  "execution":{"durationSeconds":17,"durationBindings":[{"relationTypes":["requested_duration"]}]},
                  "output":{"samples":[{"value":"SECRET_OUTPUT_VALUE"}]},
                  "visualization":{"scene":"SECRET_SCENE","sceneGraph":{"nodes":["SECRET_VISUALIZATION"]}}
                }
                """);
        CandidateContractProjection candidate = CandidateContractProjection.from(
                "motion_schema", "3.2", "DYNAMICS", "Motion", definition);

        assertEquals("3.2", candidate.schemaVersion());
        assertEquals("motion_model", candidate.modelId());
        assertEquals(List.of("contact", "requested_duration"), candidate.relationTypes());
        assertEquals(List.of("time_limit", "threshold"), candidate.endConditionCapabilities());
        QuantityProjection mass = candidate.requiredQuantities().getFirst();
        assertEquals(List.of("m"), mass.aliases());
        assertEquals(List.of("m"), mass.symbols());
        assertEquals(List.of("kg"), mass.acceptedInputUnits());
        assertEquals(Boolean.TRUE, mass.constraints().get("positive"));
        QuantityProjection gravity = candidate.optionalQuantities().getFirst();
        assertEquals("9.81", gravity.defaultValue().toPlainString());
        assertEquals(Boolean.FALSE, gravity.constraints().get("integer"));
        assertEquals("length", gravity.constraints().get("sameUnitAs"));

        ExtractionPromptBuilder.PromptMessages messages = new ExtractionPromptBuilder("Extract a specification.", 2, 4_000)
                .build(List.of(candidate), "A 2 kg object moves.");
        assertTrue(messages.systemMessage().contains("motion_schema"));
        assertTrue(messages.systemMessage().contains("\"schemaVersion\":\"3.2\""));
        assertTrue(messages.systemMessage().contains("\"modelId\":\"motion_model\""));
        assertTrue(messages.systemMessage().contains("\"executionDurationSeconds\":17"));
        assertFalse(messages.systemMessage().contains("secret_solver_id"));
        assertFalse(messages.systemMessage().contains("secret_reference_id"));
        assertFalse(messages.systemMessage().contains("SECRET_FORMULA"));
        assertFalse(messages.systemMessage().contains("SECRET_OUTPUT_VALUE"));
        assertFalse(messages.systemMessage().contains("SECRET_SCENE"));
        assertFalse(messages.systemMessage().contains("SECRET_VISUALIZATION"));
        assertFalse(messages.systemMessage().contains("A 2 kg object moves."));
        assertEquals("A 2 kg object moves.", messages.userMessage());
    }

    @Test
    void rejectsEmptyAndOverBoundCandidateSets() throws Exception {
        CandidateContractProjection candidate = candidate("only_candidate");
        ExtractionPromptBuilder builder = new ExtractionPromptBuilder("Base rules", 1, 4_000);

        assertThrows(IllegalArgumentException.class, () -> builder.build(List.of(), "request"));
        assertThrows(IllegalArgumentException.class,
                () -> builder.build(List.of(candidate, candidate("second_candidate")), "request"));
    }

    @Test
    void rejectsCombinedSystemPromptAndRequestTextThatExceedsConfiguredCharacterLimit() throws Exception {
        CandidateContractProjection candidate = candidate("oversized_candidate");
        ExtractionPromptBuilder builder = new ExtractionPromptBuilder("Base rules", 1, 4_000);

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> builder.build(List.of(candidate), "x".repeat(4_000)));
        assertTrue(exception.getMessage().contains("character limit"));
    }

    @Test
    void addsOnlyShortRetryInstructionAndEnforcesTheTotalCharacterLimit() throws Exception {
        CandidateContractProjection candidate = candidate("retry_candidate");
        ExtractionPromptBuilder builder = new ExtractionPromptBuilder("Base rules", 1, 4_000);
        var initial = builder.build(List.of(candidate), "Original physics request.");
        List<Map<String, Object>> messages = List.of(
                Map.of("role", "system", "content", initial.systemMessage()),
                Map.of("role", "user", "content", initial.userMessage()));
        String repair = "Regenerate the JSON using the unchanged request and candidate contract.";

        List<Map<String, Object>> retried = builder.appendRetryInstruction(messages, repair);

        assertEquals(messages.size() + 1, retried.size());
        assertEquals(messages.getFirst(), retried.getFirst());
        assertEquals("user", retried.getLast().get("role"));
        assertEquals(repair, retried.getLast().get("content"));
        assertTrue(builder.messageCharacterCount(retried) <= 4_000);
        assertFalse(retried.stream().anyMatch(message -> "assistant".equals(message.get("role"))));
        assertThrows(IllegalArgumentException.class,
                () -> builder.appendRetryInstruction(retried, "x".repeat(4_000)));
    }

    private CandidateContractProjection candidate(String schemaId) throws Exception {
        JsonNode definition = objectMapper.readTree("""
                {"model":"simple_model","requiredQuantities":[
                  {"key":"distance","aliases":["x"],"allowedUnits":["m"]}
                ]}
                """);
        return CandidateContractProjection.from(schemaId, "1.0", "KINEMATICS", "Simple motion", definition);
    }
}
