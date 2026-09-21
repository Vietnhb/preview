package com.example.backend.physics;

import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.compatibility.legacy.reference.thermal.IdealGasReferenceSolver;
import com.example.backend.physics.compatibility.legacy.solver.thermal.IdealGasSolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IdealGasPhysicsTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final IdealGasSolver solver = new IdealGasSolver();
    private final IdealGasReferenceSolver reference = new IdealGasReferenceSolver();

    @Test
    void isothermalProcessSatisfiesBoyleLawAndMatchesReference() {
        ObjectNode specification = mapper.createObjectNode();
        specification.put("model", "ideal_gas_isothermal");
        specification.put("amount_of_substance", 1);
        specification.put("temperature", 300);
        specification.put("initial_volume", 1);
        specification.put("volume_rate", 1);
        SolverOutput output = solver.solve(specification, Map.of(), 1, 0.25);

        double p0 = output.values().get("pressure").get(0);
        double p1 = output.values().get("pressure").get(4);
        double v0 = output.values().get("volume").get(0);
        double v1 = output.values().get("volume").get(4);
        assertEquals(p0 * v0, p1 * v1, 1e-10);
        var expected = reference.solve(specification, Map.of(), 0.75);
        assertEquals(expected.values().get("pressure"), output.values().get("pressure").get(3), 1e-10);
        assertTrue(output.values().get("work").get(4) > 0);
    }
}
