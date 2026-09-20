package com.example.backend.physics.module.dynamics;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.PhysicalConstants;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.PhysicsModule;
import com.example.backend.physics.module.SimulationClock;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Typed gravity-field and circular-orbit model with an independent closed-form oracle. */
public final class GravityOrbitModule implements PhysicsModule<GravityOrbitModule.Parameters> {
    public static final String MODULE_ID = "gravity_orbit";
    public static final String NUMERICAL_SOLVER_ID = "gravity_orbit_solver";
    public static final String REFERENCE_SOLVER_ID = "gravity_orbit_reference";

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        return new Parameters(
                quantities.require("central_mass"),
                quantities.require("satellite_mass"),
                quantities.require("orbit_radius"));
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        List<Double> time = clock.sampleTimes();
        double gravitationalParameter = PhysicalConstants.GRAVITATIONAL_CONSTANT * parameters.centralMass();
        double field = gravitationalParameter / parameters.orbitRadius() / parameters.orbitRadius();
        double force = field * parameters.satelliteMass();
        double speed = Math.sqrt(gravitationalParameter) / Math.sqrt(parameters.orbitRadius());
        double period = 2.0 * Math.PI * (parameters.orbitRadius() / speed);
        requireFiniteResults(force, field, speed, period);

        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("gravitationalForce", Collections.nCopies(time.size(), force));
        values.put("gravitationalField", Collections.nCopies(time.size(), field));
        values.put("orbitalSpeed", Collections.nCopies(time.size(), speed));
        values.put("orbitalPeriod", Collections.nCopies(time.size(), period));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        requireCheckpointTime(timeSeconds);

        // Use Newton's direct force law, the circular speed, and Kepler's third law
        // independently of the numerical output's field-times-mass and circumference/speed path.
        double radius = parameters.orbitRadius();
        double massProduct = PhysicalConstants.GRAVITATIONAL_CONSTANT * parameters.centralMass();
        double radiusSquared = radius * radius;
        double force = (massProduct * parameters.satelliteMass()) / radiusSquared;
        double field = massProduct / radiusSquared;
        double speed = Math.sqrt(massProduct / radius);
        double period = 2.0 * Math.PI * Math.sqrt((radius * radius * radius) / massProduct);
        requireFiniteResults(force, field, speed, period);

        return new AnalyticalPoint(Map.of(
                "gravitationalForce", force,
                "gravitationalField", field,
                "orbitalSpeed", speed,
                "orbitalPeriod", period));
    }

    private static void requireFiniteResults(double force, double field, double speed, double period) {
        if (!Double.isFinite(force) || !Double.isFinite(field)
                || !Double.isFinite(speed) || !Double.isFinite(period)) {
            throw new IllegalArgumentException("Gravity-orbit outputs exceed the finite numeric domain");
        }
    }

    private static void requireCheckpointTime(double timeSeconds) {
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0) {
            throw new IllegalArgumentException("timeSeconds must be finite and non-negative");
        }
    }

    public record Parameters(double centralMass, double satelliteMass, double orbitRadius) {
        public Parameters {
            if (!Double.isFinite(centralMass) || centralMass <= 0.0
                    || !Double.isFinite(satelliteMass) || satelliteMass <= 0.0
                    || !Double.isFinite(orbitRadius) || orbitRadius <= 0.0) {
                throw new IllegalArgumentException("Gravity orbit requires finite positive masses and radius");
            }
        }
    }
}
