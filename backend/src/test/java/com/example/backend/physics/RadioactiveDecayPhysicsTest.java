package com.example.backend.physics;

import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.reference.modern.RadioactiveDecayReferenceSolver;
import com.example.backend.physics.solver.modern.RadioactiveDecaySolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class RadioactiveDecayPhysicsTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final RadioactiveDecaySolver solver = new RadioactiveDecaySolver();
    private final RadioactiveDecayReferenceSolver reference = new RadioactiveDecayReferenceSolver();

    @Test
    void halfLifeAndActivityMatchIndependentReference() {
        ObjectNode specification = mapper.createObjectNode();
        specification.put("model", "radioactive_decay");
        specification.put("initial_count", 1000);
        specification.put("decay_constant", Math.log(2));
        SolverOutput output = solver.solve(specification, Map.of(), 1, 0.25);
        assertEquals(500, output.values().get("remainingCount").get(4), 1e-10);
        var expected = reference.solve(specification, Map.of(), 0.75);
        assertEquals(expected.values().get("activity"), output.values().get("activity").get(3), 1e-10);
    }
}
