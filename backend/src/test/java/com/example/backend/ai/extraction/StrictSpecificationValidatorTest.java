package com.example.backend.ai.extraction;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class StrictSpecificationValidatorTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void acceptsCanonicalNumericSpecification() throws Exception {
        assertDoesNotThrow(() -> StrictSpecificationValidator.validate(mapper.readTree("""
                {"schemaVersion":"1.0","topic":"DYNAMICS","schemaId":"hooke_law",
                 "objects":[],"quantities":[{"name":"spring_constant","symbol":"k","value":2.0,
                 "originalUnit":"N/m","confidence":1.0}],"relations":[],
                 "endCondition":{"type":"time_limit","duration":1},"confidence":1.0,"ambiguities":[]}
                """)));
    }

    @Test
    void rejectsNumericStringsAndUnknownFieldsBeforeBinding() throws Exception {
        ObjectNode root = (ObjectNode) mapper.readTree("""
                {"schemaVersion":"1.0","schemaId":"hooke_law","objects":[],"quantities":[],
                 "relations":[],"endCondition":{"type":"time_limit","duration":1},
                 "confidence":1.0,"ambiguities":[]}
                """);
        root.withArray("quantities").addObject().put("name", "spring_constant")
                .put("value", "2").put("originalUnit", "N/m");
        assertThrows(IllegalArgumentException.class, () -> StrictSpecificationValidator.validate(root));
        root.withArray("quantities").removeAll();
        root.put("unexpected", true);
        assertThrows(IllegalArgumentException.class, () -> StrictSpecificationValidator.validate(root));
    }

    @Test
    void rejectsDuplicateQuantityAndAmbiguityIdentity() throws Exception {
        ObjectNode root = (ObjectNode) mapper.readTree("""
                {"schemaVersion":"1.0","schemaId":"hooke_law","objects":[],
                 "quantities":[{"name":"k","value":2,"originalUnit":"N/m"},
                 {"name":"k","value":3,"originalUnit":"N/m"}],"relations":[],
                 "endCondition":{"type":"time_limit","duration":1},"confidence":1.0,
                 "ambiguities":[]}
                """);
        assertThrows(IllegalArgumentException.class, () -> StrictSpecificationValidator.validate(root));
        root.withArray("quantities").removeAll();
        root.withArray("ambiguities").addObject().put("code", "missing.k")
                .put("fieldPath", "quantities.k").put("question", "k?").putArray("options");
        root.withArray("ambiguities").addObject().put("code", "other")
                .put("fieldPath", "quantities.k").put("question", "k?").putArray("options");
        assertThrows(IllegalArgumentException.class, () -> StrictSpecificationValidator.validate(root));
    }
}
