package com.example.backend.physics;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.Map;

class PhysicsSolverTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void kinematicsMatchesClosedForm() throws Exception {
        var solver = new KinematicsSolver();
        var reference = new KinematicsReferenceSolver();
        var specification = mapper.readTree("{\"schemaId\":\"kinematics_1d\",\"model\":\"uniform_acceleration_1d\"}");
        var inputs = Map.of("position", 0d, "velocity", 10d, "acceleration", 2d);
        var output = solver.solve(specification, inputs, 2, .1);
        var expected = reference.solve(specification, inputs, 2);
        assertThat(output.values().get("x").get(output.values().get("x").size() - 1)).isCloseTo(expected.values().get("x"), org.assertj.core.data.Offset.offset(1e-9));
        assertThat(output.values()).containsKeys("ax", "ay");
        assertThat(output.values().get("ax").get(output.values().get("ax").size() - 1)).isCloseTo(expected.values().get("ax"), org.assertj.core.data.Offset.offset(1e-9));
        assertThat(output.values().get("ay").get(output.values().get("ay").size() - 1)).isCloseTo(expected.values().get("ay"), org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void negativeAccelerationChangesMotionButNotRequestedTimeline() throws Exception {
        var solver = new KinematicsSolver();
        var specification = mapper.readTree("{\"schemaId\":\"kinematics_1d\",\"model\":\"uniform_acceleration_1d\"}");
        var positive = solver.solve(specification, Map.of("position", 0d, "velocity", 10d, "acceleration", 2d), 8, .05);
        var negative = solver.solve(specification, Map.of("position", 0d, "velocity", 10d, "acceleration", -2d), 8, .05);
        int last = positive.time().size() - 1;

        assertThat(positive.time().get(last)).isEqualTo(8d);
        assertThat(negative.time().get(last)).isEqualTo(8d);
        assertThat(positive.values().get("x").get(last)).isCloseTo(144d, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(negative.values().get("x").get(last)).isCloseTo(16d, org.assertj.core.data.Offset.offset(1e-9));
        assertThat(negative.values().get("vx").get(last)).isCloseTo(-6d, org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void projectileUsesGravityClosedForm() throws Exception {
        var solver = new KinematicsSolver();
        var reference = new KinematicsReferenceSolver();
        var specification = mapper.readTree("{\"schemaId\":\"kinematics_projectile\",\"model\":\"projectile_2d\"}");
        var inputs = Map.of("position", 0d, "height", 0d, "velocity", 10d, "angle", 0d);
        var output = solver.solve(specification, inputs, 1, .1);
        var expected = reference.solve(specification, inputs, 1);
        assertThat(output.values().get("y").get(output.values().get("y").size() - 1)).isCloseTo(expected.values().get("y"), org.assertj.core.data.Offset.offset(1e-9));
    }

    @Test
    void dynamicsAndCircuitFamiliesProduceValidatedFields() throws Exception {
        var dynamics = new DynamicsSolver();
        var dynamicsSpec = mapper.readTree("{\"schemaId\":\"dynamics_forces\",\"model\":\"forces_1d\"}");
        assertThat(dynamics.solve(dynamicsSpec, Map.of("mass", 2d, "force", 10d, "friction", 0d,
                "position", 0d, "velocity", 0d), 1, .1).values()).containsKeys("x", "vx", "ax", "force");
        var circuit = new CircuitSolver();
        var circuitSpec = mapper.readTree("{\"schemaId\":\"circuits_rc_charging\",\"model\":\"rc_charging\"}");
        assertThat(circuit.solve(circuitSpec, Map.of("voltage", 12d, "resistance", 6d, "capacitance", 1d), 1, .1).values()).containsKeys("voltage", "current");
    }

    @Test
    void dynamicCollisionCalculatesRealCollisionTime() throws Exception {
        var dynamics = new DynamicsSolver();
        var collisionSpec = mapper.readTree("{\"schemaId\":\"dynamics_collision\",\"model\":\"elastic_collision_1d\"}");
        // x1=0, x2=6, v1=3, v2=1 -> t_coll = (6-0)/(3-1) = 3.0s, x_coll = 9.0m
        var inputs = Map.of("m1", 2d, "m2", 3d, "x1", 0d, "x2", 6d, "velocity_1", 3d, "velocity_2", 1d);
        var output = dynamics.solve(collisionSpec, inputs, 4, 0.05);
        assertThat(output.values().get("collisionTime").get(0)).isEqualTo(3.0);
        assertThat(output.values().get("collisionX").get(0)).isEqualTo(9.0);
        var expected = new DynamicsReferenceSolver().solve(collisionSpec, inputs, 3.5);
        assertThat(expected.values()).containsKeys("x1", "x2", "v1", "v2");
    }

    @Test
    void missingInputsAreRejectedInsteadOfDefaulted() throws Exception {
        var specification = mapper.readTree("{\"schemaId\":\"dynamics_collision\",\"model\":\"elastic_collision_1d\"}");
        var solver = new DynamicsSolver();
        var inputs = Map.of("m1", 2d);
        assertThatThrownBy(() -> solver.solve(specification, inputs, 4, .05))
                .isInstanceOf(IllegalArgumentException.class).hasMessageContaining("mass_2");
    }
}
