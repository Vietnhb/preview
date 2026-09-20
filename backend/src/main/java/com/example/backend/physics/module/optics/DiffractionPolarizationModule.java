package com.example.backend.physics.module.optics;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.PhysicsModule;
import com.example.backend.physics.module.SimulationClock;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Typed single-slit diffraction and Malus-law polarization module. */
public final class DiffractionPolarizationModule
        implements PhysicsModule<DiffractionPolarizationModule.Parameters> {
    public static final String MODULE_ID = "diffraction_polarization";
    public static final String NUMERICAL_SOLVER_ID = "diffraction_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "diffraction_reference_v2";

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        Objects.requireNonNull(quantities, "quantities");
        requireUnit(quantities, "wavelength", "m");
        requireUnit(quantities, "slit_width", "m");
        requireUnit(quantities, "diffraction_order", "1");
        requireUnit(quantities, "input_intensity", "W/m2");
        requireUnit(quantities, "analyzer_angle", "rad");
        return new Parameters(quantities.require("wavelength"), quantities.require("slit_width"),
                quantities.require("diffraction_order"), quantities.require("input_intensity"),
                quantities.require("analyzer_angle"));
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        Objects.requireNonNull(parameters, "parameters");
        List<Double> time = Objects.requireNonNull(clock, "clock").sampleTimes();

        double sineAngle = parameters.diffractionOrder() * parameters.wavelength() / parameters.slitWidth();
        boolean minimumExists = parameters.diffractionOrder() >= 1.0 && sineAngle <= 1.0;
        double diffractionAngle = minimumExists ? Math.asin(sineAngle) : 0.0;
        double analyzerCosine = Math.cos(parameters.analyzerAngle());
        double transmittedIntensity = parameters.inputIntensity() * analyzerCosine * analyzerCosine;
        requireFinite("diffractionAngle", diffractionAngle);
        requireFinite("transmittedIntensity", transmittedIntensity);

        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("diffractionAngle", Collections.nCopies(time.size(), diffractionAngle));
        values.put("minimumExists", Collections.nCopies(time.size(), minimumExists ? 1.0 : 0.0));
        values.put("transmittedIntensity", Collections.nCopies(time.size(), transmittedIntensity));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        Objects.requireNonNull(parameters, "parameters");
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0.0) {
            throw new IllegalArgumentException("Diffraction reference time must be finite and non-negative");
        }

        // Reorder the grating ratio and use atan2 for the valid angular solution;
        // this oracle does not call the numerical path's asin helper.
        double sineAngle = parameters.diffractionOrder() == 0.0 ? 0.0
                : (parameters.wavelength() / parameters.slitWidth()) * parameters.diffractionOrder();
        boolean minimumExists = parameters.diffractionOrder() >= 1.0 && sineAngle <= 1.0;
        double diffractionAngle = 0.0;
        if (minimumExists) {
            double cosineMagnitude = Math.sqrt(Math.max(0.0, 1.0 - sineAngle * sineAngle));
            diffractionAngle = Math.atan2(sineAngle, cosineMagnitude);
        }
        double analyzerCosine = Math.cos(parameters.analyzerAngle());
        double transmittedIntensity = (parameters.inputIntensity() * analyzerCosine) * analyzerCosine;
        requireFinite("reference diffractionAngle", diffractionAngle);
        requireFinite("reference transmittedIntensity", transmittedIntensity);
        return new AnalyticalPoint(Map.of("diffractionAngle", diffractionAngle,
                "minimumExists", minimumExists ? 1.0 : 0.0,
                "transmittedIntensity", transmittedIntensity));
    }

    private static void requireUnit(CanonicalQuantityBag quantities, String key, String unit) {
        if (!unit.equals(quantities.unit(key))) {
            throw new IllegalArgumentException("Canonical diffraction quantity " + key + " must use unit " + unit);
        }
    }

    private static void requireFinite(String outputKey, double value) {
        if (!Double.isFinite(value)) {
            throw new ArithmeticException("Diffraction and polarization result is not finite: " + outputKey);
        }
    }

    /** SI-valued input data after canonical schema ingress. */
    public record Parameters(double wavelength, double slitWidth, double diffractionOrder,
                             double inputIntensity, double analyzerAngle) {
        public Parameters {
            if (!Double.isFinite(wavelength) || wavelength <= 0.0
                    || !Double.isFinite(slitWidth) || slitWidth <= 0.0
                    || !Double.isFinite(diffractionOrder) || diffractionOrder < 0.0
                    || diffractionOrder != Math.rint(diffractionOrder)
                    || !Double.isFinite(inputIntensity) || inputIntensity < 0.0
                    || !Double.isFinite(analyzerAngle)) {
                throw new IllegalArgumentException("Diffraction inputs must be finite and in their physical domain");
            }
        }
    }
}
