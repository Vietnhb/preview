package com.example.backend.physics;

import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.compatibility.legacy.reference.optics.ThinLensReferenceSolver;
import com.example.backend.physics.compatibility.legacy.solver.optics.ThinLensSolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ThinLensPhysicsTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final ThinLensSolver solver = new ThinLensSolver();
    private final ThinLensReferenceSolver reference = new ThinLensReferenceSolver();

    @Test
    void thinLensEquationMatchesReferenceAndMagnification() {
        ObjectNode specification = mapper.createObjectNode();
        specification.put("model", "thin_lens_imaging");
        specification.put("focal_length", 0.1);
        specification.put("object_distance", 0.3);
        specification.put("object_height", 0.02);
        SolverOutput output = solver.solve(specification, Map.of(), 1, 0.2);
        assertEquals(0.15, output.values().get("imageDistance").get(0), 1e-12);
        assertEquals(-0.01, output.values().get("imageHeight").get(0), 1e-12);
        var expected = reference.solve(specification, Map.of(), 0.5);
        assertEquals(expected.values().get("magnification"), output.values().get("magnification").get(2), 1e-12);
    }
}
