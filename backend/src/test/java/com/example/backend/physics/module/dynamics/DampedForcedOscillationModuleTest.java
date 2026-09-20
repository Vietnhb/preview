package com.example.backend.physics.module.dynamics;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.model.dynamics.DampedForcedOscillationParameters;
import com.example.backend.physics.module.SimulationClock;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class DampedForcedOscillationModuleTest {
    private static final double TOLERANCE = 1.0e-12;
    private final DampedForcedOscillationModule module = new DampedForcedOscillationModule();

    @Test
    void underdampedGoldenCaseMatchesIndependentClosedFormAndInitialConditions() {
        DampedForcedOscillationParameters parameters = module.bind(quantities(2.0));
        double time = Math.PI / (2.0 * Math.sqrt(3.0));
        double envelope = Math.exp(-time);
        double expectedX = 0.5 + envelope / (2.0 * Math.sqrt(3.0));
        double expectedV = -2.0 * envelope / Math.sqrt(3.0);
        double expectedA = 2.0 * envelope / Math.sqrt(3.0);
        double expectedEnergy = 0.5 * expectedV * expectedV + 2.0 * expectedX * expectedX;

        SolverOutput numerical = module.solve(parameters, new SimulationClock(time, time));
        AnalyticalPoint oracle = module.referenceAt(parameters, time);

        assertInitialState(parameters, numerical);
        assertEquals(expectedX, numerical.values().get("displacement").get(1), TOLERANCE);
        assertEquals(expectedV, numerical.values().get("velocity").get(1), TOLERANCE);
        assertEquals(expectedA, numerical.values().get("acceleration").get(1), TOLERANCE);
        assertEquals(expectedEnergy, numerical.values().get("mechanicalEnergy").get(1), TOLERANCE);
        assertEquals(2.0, numerical.values().get("drivingForce").get(1), TOLERANCE);
        assertOracle(oracle, expectedX, expectedV, expectedA, expectedEnergy, 2.0);
    }

    @Test
    void criticallyDampedGoldenCaseMatchesIndependentClosedFormAndInitialConditions() {
        DampedForcedOscillationParameters parameters = module.bind(quantities(4.0));
        double time = 0.5;
        double envelope = Math.exp(-1.0);
        double expectedX = 0.5 + envelope;
        double expectedV = -envelope;
        double expectedA = 0.0;
        double expectedEnergy = 0.5 * expectedV * expectedV + 2.0 * expectedX * expectedX;

        SolverOutput numerical = module.solve(parameters, new SimulationClock(time, time));
        AnalyticalPoint oracle = module.referenceAt(parameters, time);

        assertInitialState(parameters, numerical);
        assertEquals(expectedX, numerical.values().get("displacement").get(1), TOLERANCE);
        assertEquals(expectedV, numerical.values().get("velocity").get(1), TOLERANCE);
        assertEquals(expectedA, numerical.values().get("acceleration").get(1), TOLERANCE);
        assertEquals(expectedEnergy, numerical.values().get("mechanicalEnergy").get(1), TOLERANCE);
        assertEquals(2.0, numerical.values().get("drivingForce").get(1), TOLERANCE);
        assertOracle(oracle, expectedX, expectedV, expectedA, expectedEnergy, 2.0);
    }

    @Test
    void veryLowFrequencyUndampedCaseIsNotMisclassifiedAsCritical() {
        DampedForcedOscillationParameters parameters = module.bind(
                quantities(1.0, 1.0e-26, 0.0, 0.0, 0.0, 1.0, 0.0));
        double time = 1.0e13;
        double phase = 1.0;
        double expectedX = Math.cos(phase);
        double expectedV = -1.0e-13 * Math.sin(phase);
        double expectedA = -1.0e-26 * Math.cos(phase);
        double expectedEnergy = 0.5e-26;

        SolverOutput numerical = module.solve(parameters, new SimulationClock(time, time));
        AnalyticalPoint oracle = module.referenceAt(parameters, time);

        assertEquals(expectedX, numerical.values().get("displacement").get(1), 1.0e-12);
        assertEquals(expectedV, numerical.values().get("velocity").get(1), 1.0e-27);
        assertEquals(expectedA, numerical.values().get("acceleration").get(1), 1.0e-39);
        assertEquals(expectedEnergy, numerical.values().get("mechanicalEnergy").get(1), 1.0e-39);
        assertEquals(expectedX, oracle.values().get("displacement"), 1.0e-12);
        assertEquals(expectedV, oracle.values().get("velocity"), 1.0e-27);
        assertEquals(expectedA, oracle.values().get("acceleration"), 1.0e-39);
        assertEquals(expectedEnergy, oracle.values().get("mechanicalEnergy"), 1.0e-39);
    }

    @Test
    void overdampedCaseMatchesIndependentHyperbolicClosedForm() {
        DampedForcedOscillationParameters parameters = module.bind(
                quantities(1.0, 4.0, 8.0, 0.0, 0.0, 1.0, 0.0));
        double time = 0.5;
        double gamma = 4.0;
        double naturalFrequency = 2.0;
        double root = Math.sqrt(gamma * gamma - naturalFrequency * naturalFrequency);
        double exponential = Math.exp(-gamma * time);
        double expectedX = exponential * Math.cosh(root * time)
                + (gamma / root) * exponential * Math.sinh(root * time);
        double expectedV = -(naturalFrequency * naturalFrequency / root)
                * exponential * Math.sinh(root * time);
        double expectedA = -2.0 * gamma * expectedV
                - naturalFrequency * naturalFrequency * expectedX;
        double expectedEnergy = 0.5 * expectedV * expectedV + 0.5 * 4.0 * expectedX * expectedX;

        SolverOutput numerical = module.solve(parameters, new SimulationClock(time, time));
        AnalyticalPoint oracle = module.referenceAt(parameters, time);

        assertEquals(expectedX, numerical.values().get("displacement").get(1), TOLERANCE);
        assertEquals(expectedV, numerical.values().get("velocity").get(1), TOLERANCE);
        assertEquals(expectedA, numerical.values().get("acceleration").get(1), TOLERANCE);
        assertEquals(expectedEnergy, numerical.values().get("mechanicalEnergy").get(1), TOLERANCE);
        assertEquals(expectedX, oracle.values().get("displacement"), TOLERANCE);
        assertEquals(expectedV, oracle.values().get("velocity"), TOLERANCE);
        assertEquals(expectedA, oracle.values().get("acceleration"), TOLERANCE);
        assertEquals(expectedEnergy, oracle.values().get("mechanicalEnergy"), TOLERANCE);
    }

    @Test
    void referenceRejectsInvalidCheckpointTime() {
        DampedForcedOscillationParameters parameters = module.bind(quantities(2.0));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(parameters, -0.01));
        assertThrows(IllegalArgumentException.class, () -> module.referenceAt(parameters, Double.NaN));
    }

    private static CanonicalQuantityBag quantities(double damping) {
        return quantities(1.0, 4.0, damping, 2.0, 0.0, 1.0, 0.0);
    }

    private static CanonicalQuantityBag quantities(double mass, double springConstant,
                                                   double damping, double forceAmplitude,
                                                   double driveFrequency, double initialDisplacement,
                                                   double initialVelocity) {
        return new CanonicalQuantityBag(
                Map.of(
                        "mass", BigDecimal.valueOf(mass),
                        "spring_constant", BigDecimal.valueOf(springConstant),
                        "damping_coefficient", BigDecimal.valueOf(damping),
                        "driving_force_amplitude", BigDecimal.valueOf(forceAmplitude),
                        "driving_frequency", BigDecimal.valueOf(driveFrequency),
                        "initial_displacement", BigDecimal.valueOf(initialDisplacement),
                        "initial_velocity", BigDecimal.valueOf(initialVelocity)),
                Map.of(
                        "mass", "kg",
                        "spring_constant", "N/m",
                        "damping_coefficient", "kg/s",
                        "driving_force_amplitude", "N",
                        "driving_frequency", "rad/s",
                        "initial_displacement", "m",
                        "initial_velocity", "m/s"));
    }

    private static void assertInitialState(DampedForcedOscillationParameters parameters, SolverOutput output) {
        assertEquals(parameters.initialDisplacement(), output.values().get("displacement").get(0), TOLERANCE);
        assertEquals(parameters.initialVelocity(), output.values().get("velocity").get(0), TOLERANCE);
        double expectedAcceleration = (parameters.drivingAmplitude()
                - parameters.dampingCoefficient() * parameters.initialVelocity()
                - parameters.springConstant() * parameters.initialDisplacement()) / parameters.mass();
        assertEquals(expectedAcceleration, output.values().get("acceleration").get(0), TOLERANCE);
    }

    private static void assertOracle(AnalyticalPoint oracle, double x, double v, double a,
                                     double energy, double drivingForce) {
        assertEquals(x, oracle.values().get("displacement"), TOLERANCE);
        assertEquals(v, oracle.values().get("velocity"), TOLERANCE);
        assertEquals(a, oracle.values().get("acceleration"), TOLERANCE);
        assertEquals(energy, oracle.values().get("mechanicalEnergy"), TOLERANCE);
        assertEquals(drivingForce, oracle.values().get("drivingForce"), TOLERANCE);
    }
}
