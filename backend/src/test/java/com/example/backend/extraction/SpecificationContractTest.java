package com.example.backend.extraction;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;

class SpecificationContractTest {

    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void specificationV1ContainsRequiredContractFields() {
        SpecificationDocument document = new SpecificationDocument(
                "1.0",
                "KINEMATICS",
                "kinematics_1d",
                List.of(new PhysicalObject("car", "Car", "vehicle")),
                List.of(new PhysicalQuantity(
                        "velocity", "v", new BigDecimal("36"), "km/h",
                        new BigDecimal("10"), "m/s", new BigDecimal("0.99"), "36 km/h")),
                List.of(),
                new BigDecimal("0.99"),
                List.of());

        JsonNode json = objectMapper.valueToTree(document);

        assertThat(json.path("schemaVersion").asText()).isEqualTo("1.0");
        assertThat(json.has("topic")).isTrue();
        assertThat(json.path("schemaId").asText()).isEqualTo("kinematics_1d");
        assertThat(json.has("objects")).isTrue();
        assertThat(json.has("quantities")).isTrue();
        assertThat(json.has("relations")).isTrue();
        assertThat(json.has("confidence")).isTrue();
        assertThat(json.has("ambiguities")).isTrue();
        assertThat(json.path("quantities").path(0).path("originalUnit").asText()).isEqualTo("km/h");
        assertThat(json.path("quantities").path(0).path("normalizedUnit").asText()).isEqualTo("m/s");
    }

    @Test
    void relationValueAcceptsQualitativeConditions() throws Exception {
        SpecificationDocument document = objectMapper.readValue("""
                {"schemaVersion":"1.0","topic":"DYNAMICS","schemaId":"dynamics_collision",
                 "objects":[],"quantities":[],
                 "relations":[{"type":"collision_type","subject":"body1","object":"body2",
                   "value":"elastic","unit":null,"sourceText":"collide elastically"}],
                 "confidence":0.9,"ambiguities":[]}
                """, SpecificationDocument.class);

        assertThat(document.relations().getFirst().value().asText()).isEqualTo("elastic");
    }
}
