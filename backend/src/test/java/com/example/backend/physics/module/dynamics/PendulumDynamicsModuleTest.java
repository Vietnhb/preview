package com.example.backend.physics.module.dynamics;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.Map;

import org.junit.jupiter.api.Test;

import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.module.SimulationClock;

class PendulumDynamicsModuleTest {
    @Test
    void solvesTheNonlinearEquationAndConservesEnergy() {
        var module = new PendulumDynamicsModule();
        var quantities = new CanonicalQuantityBag(
                Map.of("length", BigDecimal.valueOf(1.2),
                        "initial_angle", BigDecimal.valueOf(Math.toRadians(20)),
                        "initial_angular_velocity", BigDecimal.ZERO,
                        "gravitational_acceleration", BigDecimal.valueOf(9.81)),
                Map.of("length", "m", "initial_angle", "rad",
                        "initial_angular_velocity", "rad/s", "gravitational_acceleration", "m/s2"));

        var output = module.solve(module.bind(quantities), new SimulationClock(12.0, 0.02));

        assertThat(output.time()).hasSize(601);
        assertThat(output.positions().get("x")).hasSize(601);
        assertThat(output.positions().get("y")).hasSize(601);
        assertThat(output.values().get("angle").getFirst()).isCloseTo(Math.toRadians(20),
                org.assertj.core.data.Offset.offset(1e-12));
        var energy = output.values().get("mechanical_energy_per_mass");
        double baseline = energy.getFirst();
        assertThat(energy).allSatisfy(value -> assertThat(value).isCloseTo(baseline,
                org.assertj.core.data.Offset.offset(1e-8)));
    }

    @Test
    void stationaryEquilibriumRemainsStationary() {
        var module = new PendulumDynamicsModule();
        var quantities = new CanonicalQuantityBag(
                Map.of("length", BigDecimal.ONE, "initial_angle", BigDecimal.ZERO,
                        "initial_angular_velocity", BigDecimal.ZERO,
                        "gravitational_acceleration", BigDecimal.valueOf(9.81)),
                Map.of("length", "m", "initial_angle", "rad",
                        "initial_angular_velocity", "rad/s", "gravitational_acceleration", "m/s2"));
        var output = module.solve(module.bind(quantities), new SimulationClock(2.0, 0.02));
        assertThat(output.values().get("angle")).containsOnly(0.0);
        assertThat(output.values().get("angular_velocity")).containsOnly(0.0);
    }
}
