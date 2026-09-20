package com.example.backend.physics.module.dynamics;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.PhysicsModule;
import com.example.backend.physics.module.SimulationClock;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Typed model owner for hydrostatic pressure and Archimedean buoyancy. */
public final class HydrostaticsModule implements PhysicsModule<HydrostaticsModule.Parameters> {
    public static final String MODULE_ID = "hydrostatics";
    public static final String NUMERICAL_SOLVER_ID = "hydrostatics_solver";
    public static final String REFERENCE_SOLVER_ID = "hydrostatics_reference";

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        double density = quantities.require("fluid_density");
        double depth = quantities.require("depth");
        double displacedVolume = quantities.require("displaced_volume");
        // Optional schema defaults are materialized before this boundary.
        double gravity = quantities.require("gravitational_acceleration");
        double atmosphericPressure = quantities.require("atmospheric_pressure");
        if (density <= 0 || depth < 0 || displacedVolume < 0 || gravity <= 0 || atmosphericPressure < 0) {
            throw new IllegalArgumentException("Hydrostatics requires positive density and gravity, with non-negative depth, volume, and atmospheric pressure");
        }
        return new Parameters(density, depth, displacedVolume, gravity, atmosphericPressure);
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        List<Double> time = clock.sampleTimes();
        double gaugePressure = parameters.fluidDensity() * parameters.gravity() * parameters.depth();
        double absolutePressure = parameters.atmosphericPressure() + gaugePressure;
        double buoyantForce = parameters.fluidDensity() * parameters.gravity() * parameters.displacedVolume();
        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("gaugePressure", Collections.nCopies(time.size(), gaugePressure));
        values.put("absolutePressure", Collections.nCopies(time.size(), absolutePressure));
        values.put("buoyantForce", Collections.nCopies(time.size(), buoyantForce));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0) {
            throw new IllegalArgumentException("timeSeconds must be finite and non-negative");
        }
        // Re-evaluate from primitive state using the pressure head and displaced-mass
        // forms, independent of the numerical output path above.
        double pressureHead = parameters.gravity() * parameters.depth();
        double oracleGaugePressure = parameters.fluidDensity() * pressureHead;
        double oracleAbsolutePressure = Math.fma(parameters.fluidDensity(), pressureHead,
                parameters.atmosphericPressure());
        double displacedMass = parameters.fluidDensity() * parameters.displacedVolume();
        double oracleBuoyantForce = displacedMass * parameters.gravity();
        return new AnalyticalPoint(Map.of(
                "gaugePressure", oracleGaugePressure,
                "absolutePressure", oracleAbsolutePressure,
                "buoyantForce", oracleBuoyantForce));
    }

    public record Parameters(double fluidDensity, double depth, double displacedVolume,
                             double gravity, double atmosphericPressure) { }
}
