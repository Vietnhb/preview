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

/** Typed model owner for linear spring force and elastic potential energy. */
public final class HookeLawModule implements PhysicsModule<HookeLawModule.Parameters> {
    public static final String MODULE_ID = "hooke_law";
    public static final String NUMERICAL_SOLVER_ID = "hooke_law_solver";
    public static final String REFERENCE_SOLVER_ID = "hooke_law_reference";

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        double stiffness = quantities.require("spring_constant");
        double displacement = quantities.require("displacement");
        if (!Double.isFinite(stiffness) || stiffness <= 0 || !Double.isFinite(displacement)) {
            throw new IllegalArgumentException("Hooke law requires a finite positive spring constant and finite displacement");
        }
        return new Parameters(stiffness, displacement);
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        List<Double> time = clock.sampleTimes();
        double force = -parameters.springConstant() * parameters.displacement();
        double energy = 0.5 * parameters.springConstant()
                * parameters.displacement() * parameters.displacement();
        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("restoringForce", Collections.nCopies(time.size(), force));
        values.put("elasticPotentialEnergy", Collections.nCopies(time.size(), energy));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        requireCheckpointTime(timeSeconds);
        // Independently evaluate the closed-form quantities from primitive inputs.
        double forceByHookeLaw = -(parameters.springConstant() * parameters.displacement());
        double energyFromWork = (parameters.springConstant() / 2.0)
                * (parameters.displacement() * parameters.displacement());
        return new AnalyticalPoint(Map.of(
                "restoringForce", forceByHookeLaw,
                "elasticPotentialEnergy", energyFromWork));
    }

    private static void requireCheckpointTime(double timeSeconds) {
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0) {
            throw new IllegalArgumentException("timeSeconds must be finite and non-negative");
        }
    }

    public record Parameters(double springConstant, double displacement) { }
}
