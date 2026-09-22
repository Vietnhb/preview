package com.example.backend.physics.output;

import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.module.SimulationClock;
import com.example.backend.physics.module.dynamics.UniformAccelerationModule;
import com.example.backend.physics.validation.OutputSourceBinding;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

class PhysicsOutputFrameMapperTest {
    @Test
    void mapsRepeatedLegacyComponentNamesThroughCatalogBindings() {
        var module = new UniformAccelerationModule();
        var quantities = new CanonicalQuantityBag(
                Map.of("initial_position", BigDecimal.ZERO,
                        "initial_velocity", BigDecimal.TEN,
                        "acceleration", BigDecimal.valueOf(2)),
                Map.of("initial_position", "m", "initial_velocity", "m/s", "acceleration", "m/s2"));
        var output = module.solve(module.bind(quantities), new SimulationClock(1, .5));
        var definitions = new LinkedHashMap<String, PhysicsOutputContract.OutputDefinition>();
        definitions.put("x", definition("m"));
        definitions.put("vx", definition("m/s"));
        definitions.put("ax", definition("m/s2"));
        definitions.put("displacement", definition("m"));
        definitions.put("y", definition("m"));
        definitions.put("vy", definition("m/s"));
        definitions.put("ay", definition("m/s2"));
        var contract = new PhysicsOutputContract("kinematics", "1.11", "uniform_acceleration",
                definitions, 100);
        var bindings = Map.of(
                "x", List.of(OutputSourceBinding.declared(OutputSourceBinding.Group.POSITIONS, "x")),
                "vx", List.of(OutputSourceBinding.declared(OutputSourceBinding.Group.VELOCITIES, "x")),
                "ax", List.of(OutputSourceBinding.declared(OutputSourceBinding.Group.ACCELERATIONS, "x")));

        PhysicsOutputFrame frame = PhysicsOutputFrameMapper.fromSolverOutput(output, contract, bindings);

        assertEquals(List.of("x", "displacement", "y", "vx", "vy", "ax", "ay"),
                frame.outputs().stream().map(PhysicsOutput::key).toList());
        assertEquals(List.of(0.0, 5.25, 11.0),
                frame.outputs().stream().filter(item -> item.key().equals("x"))
                        .map(item -> ((TimeSeriesOutput) item).values()).findFirst().orElseThrow());
    }

    private static PhysicsOutputContract.OutputDefinition definition(String unit) {
        return new PhysicsOutputContract.OutputDefinition(PhysicsOutput.OutputKind.TIME_SERIES, unit, true);
    }
}
