package com.example.backend.physics;

import com.example.backend.physics.reference.circuits.CircuitReferenceSolver;
import com.example.backend.physics.reference.dynamics.DynamicsReferenceSolver;
import com.example.backend.physics.reference.kinematics.KinematicsReferenceSolver;
import com.example.backend.physics.solver.circuits.CircuitSolver;
import com.example.backend.physics.solver.dynamics.DynamicsSolver;
import com.example.backend.physics.solver.kinematics.KinematicsSolver;
import com.example.backend.physics.model.SolverOutput;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;

/** Regression coverage for the original Grade 10-12 mechanics and RC families. */
class LegacyCorePhysicsTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private ObjectNode model(String name) { return mapper.createObjectNode().put("model", name); }

    @Test void kinematicsFamiliesMatchReference() {
        ObjectNode uniform = model("uniform_acceleration").put("initial_position", 2)
                .put("initial_velocity", 3).put("acceleration", 4);
        SolverOutput uniformOutput = new KinematicsSolver().solve(uniform, Map.of(), 1, .2);
        assertEquals(7, uniformOutput.positions().get("x").get(5), 1e-12);
        assertEquals(new KinematicsReferenceSolver().solve(uniform, Map.of(), 1).values().get("vx"),
                uniformOutput.velocities().get("x").get(5), 1e-12);

        ObjectNode projectile = model("projectile").put("initial_position", 0)
                .put("initial_velocity", 10).put("initial_height", 5)
                .put("launch_angle", Math.PI / 4).put("gravitational_acceleration", 9.81);
        SolverOutput projectileOutput = new KinematicsSolver().solve(projectile, Map.of(), 1, .2);
        var projectileReference = new KinematicsReferenceSolver().solve(projectile, Map.of(), 1);
        assertEquals(projectileReference.values().get("x"), projectileOutput.positions().get("x").get(5), 1e-12);
        assertEquals(projectileReference.values().get("y"), projectileOutput.positions().get("y").get(5), 1e-12);
    }

    @Test void motionGraphModelsExposeDedicatedBindings() {
        for (String model : java.util.List.of("position_time_graph", "velocity_time_graph", "acceleration_time_graph")) {
            ObjectNode graph = model(model).put("initial_position", 2)
                    .put("initial_velocity", 3).put("acceleration", 4);
            SolverOutput output = new KinematicsSolver().solve(graph, Map.of(), 1, .2);
            var reference = new KinematicsReferenceSolver().solve(graph, Map.of(), 1);
            assertEquals(reference.values().get("x"), output.positions().get("x").get(5), 1e-12);
            assertEquals(reference.values().get("vx"), output.velocities().get("x").get(5), 1e-12);
            assertEquals(reference.values().get("ax"), output.accelerations().get("x").get(5), 1e-12);
        }
    }

    @Test void forcesCollisionAndOscillationMatchReference() {
        ObjectNode forces = model("forces").put("mass", 2).put("net_force", 10)
                .put("friction_coefficient", .1).put("initial_velocity", 0).put("initial_position", 0)
                .put("gravitational_acceleration", 9.81);
        SolverOutput forceOutput = new DynamicsSolver().solve(forces, Map.of(), 1, .2);
        var forceReference = new DynamicsReferenceSolver().solve(forces, Map.of(), 1);
        assertEquals(forceReference.values().get("x"), forceOutput.positions().get("x").get(5), 1e-12);
        assertEquals(forceReference.values().get("vx"), forceOutput.velocities().get("x").get(5), 1e-12);

        ObjectNode collision = model("elastic_collision").put("mass_1", 1).put("mass_2", 1)
                .put("initial_position_1", 0).put("initial_position_2", 1).put("velocity_1", 2).put("velocity_2", 0);
        SolverOutput collisionOutput = new DynamicsSolver().solve(collision, Map.of(), 1, .2);
        var collisionReference = new DynamicsReferenceSolver().solve(collision, Map.of(), 1);
        assertEquals(collisionReference.values().get("v1"), collisionOutput.velocities().get("v1").get(5), 1e-12);
        assertEquals(collisionReference.values().get("v2"), collisionOutput.velocities().get("v2").get(5), 1e-12);

        ObjectNode spring = model("spring").put("amplitude", .2).put("mass", 2)
                .put("spring_constant", 8).put("phase", 0);
        SolverOutput springOutput = new DynamicsSolver().solve(spring, Map.of(), .5, .125);
        var springReference = new DynamicsReferenceSolver().solve(spring, Map.of(), .5);
        assertEquals(springReference.values().get("x"), springOutput.positions().get("x").get(4), 1e-12);
        assertEquals(springReference.values().get("vx"), springOutput.velocities().get("x").get(4), 1e-12);
    }

    @Test void rcChargingAndDischargingMatchReference() {
        ObjectNode charging = model("rc_charging").put("voltage", 10).put("resistance", 1000).put("capacitance", .001);
        SolverOutput chargingOutput = new CircuitSolver().solve(charging, Map.of(), 1, .2);
        var chargingReference = new CircuitReferenceSolver().solve(charging, Map.of(), 1);
        assertEquals(chargingReference.values().get("voltage"), chargingOutput.values().get("voltage").get(5), 1e-12);
        assertEquals(chargingReference.values().get("current"), chargingOutput.values().get("current").get(5), 1e-12);

        ObjectNode discharging = model("rc_discharging").put("voltage", 10).put("resistance", 1000).put("capacitance", .001);
        SolverOutput dischargingOutput = new CircuitSolver().solve(discharging, Map.of(), 1, .2);
        var dischargingReference = new CircuitReferenceSolver().solve(discharging, Map.of(), 1);
        assertEquals(dischargingReference.values().get("voltage"), dischargingOutput.values().get("voltage").get(5), 1e-12);
        assertEquals(dischargingReference.values().get("current"), dischargingOutput.values().get("current").get(5), 1e-12);
    }
}
