package com.example.backend.physics.module.waves;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.ScalarField;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.model.WaveFieldGrid;
import com.example.backend.physics.module.PhysicsModule;
import com.example.backend.physics.module.SimulationClock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Typed two-source water-wave interference module with an independent center oracle. */
public final class WaterSurfaceInterferenceModule
        implements PhysicsModule<WaterSurfaceInterferenceModule.Parameters> {
    public static final String MODULE_ID = "water_surface_interference";
    public static final String NUMERICAL_SOLVER_ID = "water_surface_interference_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "water_surface_interference_reference_v2";
    private static final String FIELD_ID = "waterSurface";
    private static final int MIN_SPATIAL_SAMPLES = 8;
    private static final int MAX_SPATIAL_SAMPLES = 128;

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        Objects.requireNonNull(quantities, "quantities");
        double wavelength = requireCanonical(quantities, "wavelength", "m");
        double waveSpeed = requireCanonical(quantities, "wave_speed", "m/s");
        double separation = requireCanonical(quantities, "source_separation", "m");
        double amplitude = requireCanonical(quantities, "amplitude", "m");
        double domainSize = requireCanonical(quantities, "domain_size", "m");
        double rawSamples = requireCanonical(quantities, "spatial_samples", "1");
        if (rawSamples != Math.rint(rawSamples) || rawSamples < MIN_SPATIAL_SAMPLES
                || rawSamples > MAX_SPATIAL_SAMPLES) {
            throw new IllegalArgumentException("Water-surface spatial_samples must be an integer in [8,128]");
        }
        return new Parameters(wavelength, waveSpeed, separation, amplitude, domainSize, (int) rawSamples);
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        Objects.requireNonNull(parameters, "parameters");
        Objects.requireNonNull(clock, "clock");
        int timeSamples = WaveFieldGrid.timeSamples(clock.durationSeconds(), clock.stepSeconds());
        ScalarField.validateResourceShape(timeSamples, parameters.spatialSamples(), parameters.spatialSamples());

        double waveNumber = 2.0 * Math.PI / parameters.wavelength();
        double angularFrequency = 2.0 * Math.PI * parameters.waveSpeed() / parameters.wavelength();
        requirePositiveFinite(waveNumber, "wave number");
        requirePositiveFinite(angularFrequency, "angular frequency");
        List<Double> x = centeredCoordinates(parameters.domainSize(), parameters.spatialSamples());
        List<Double> y = List.copyOf(x);
        List<Double> time = WaveFieldGrid.timeCoordinates(
                clock.durationSeconds(), clock.stepSeconds(), timeSamples);
        List<List<Double>> rows = new ArrayList<>(timeSamples);
        List<Double> centerHeight = new ArrayList<>(timeSamples);
        for (double currentTime : time) {
            List<Double> row = new ArrayList<>(parameters.spatialSamples() * parameters.spatialSamples());
            for (double xCoordinate : x) {
                for (double yCoordinate : y) {
                    row.add(numericalHeight(parameters, xCoordinate, yCoordinate,
                            currentTime, waveNumber, angularFrequency));
                }
            }
            rows.add(List.copyOf(row));
            centerHeight.add(numericalHeight(parameters, 0.0, 0.0, currentTime,
                    waveNumber, angularFrequency));
        }

        double spaceStep = parameters.domainSize() / (parameters.spatialSamples() - 1.0);
        ScalarField field = new ScalarField(ScalarField.CONTRACT_VERSION, ScalarField.SCALAR_FIELD_TYPE, 2,
                List.of(new ScalarField.Axis("x", "m", x), new ScalarField.Axis("y", "m", y)),
                List.of(time.size(), x.size(), y.size()), time, rows, "m", "s",
                new ScalarField.Sampling(spaceStep, clock.stepSeconds()), "linear",
                "open;water_surface;point_sources=2");
        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put("centerHeight", List.copyOf(centerHeight));
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values, Map.of(FIELD_ID, field));
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        Objects.requireNonNull(parameters, "parameters");
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0.0) {
            throw new IllegalArgumentException("Water-surface reference time must be finite and non-negative");
        }

        // At the midpoint both radial distances equal d/2. The oracle reduces
        // the two equal source contributions to one doubled cosine, rather
        // than performing the numerical path's two distance evaluations.
        double spatialPhase = Math.PI * parameters.sourceSeparation() / parameters.wavelength();
        double temporalPhase = 2.0 * Math.PI * parameters.waveSpeed() / parameters.wavelength() * timeSeconds;
        requireFinite(spatialPhase, "reference spatial phase");
        requireFinite(temporalPhase, "reference temporal phase");
        double centerHeight = 2.0 * parameters.amplitude() * Math.cos(spatialPhase - temporalPhase);
        requireFinite(centerHeight, "reference center height");
        return new AnalyticalPoint(Map.of("centerHeight", centerHeight));
    }

    private static double numericalHeight(Parameters parameters, double x, double y, double time,
                                          double waveNumber, double angularFrequency) {
        double halfSeparation = parameters.sourceSeparation() / 2.0;
        double distanceOne = Math.hypot(x, y - halfSeparation);
        double distanceTwo = Math.hypot(x, y + halfSeparation);
        double phaseOne = waveNumber * distanceOne - angularFrequency * time;
        double phaseTwo = waveNumber * distanceTwo - angularFrequency * time;
        requireFinite(phaseOne, "source one phase");
        requireFinite(phaseTwo, "source two phase");
        double height = parameters.amplitude() * (Math.cos(phaseOne) + Math.cos(phaseTwo));
        requireFinite(height, "surface height");
        return height;
    }

    private static List<Double> centeredCoordinates(double domainSize, int samples) {
        double start = -domainSize / 2.0;
        double end = domainSize / 2.0;
        return WaveFieldGrid.spaceCoordinates(start, end, samples);
    }

    private static double requireCanonical(CanonicalQuantityBag quantities, String key, String unit) {
        if (!unit.equals(quantities.unit(key))) {
            throw new IllegalArgumentException("Canonical water-surface quantity " + key
                    + " must use unit " + unit);
        }
        double value = quantities.require(key);
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Canonical water-surface quantity must be finite: " + key);
        }
        return value;
    }

    private static void requireFinite(double value, String label) {
        if (!Double.isFinite(value)) throw new ArithmeticException("Water-surface " + label + " must be finite");
    }

    private static void requirePositiveFinite(double value, String label) {
        requireFinite(value, label);
        if (value <= 0.0) throw new ArithmeticException("Water-surface " + label + " must be positive");
    }

    /** SI quantities after schema version binding, alias resolution and unit normalization. */
    public record Parameters(double wavelength, double waveSpeed, double sourceSeparation,
                             double amplitude, double domainSize, int spatialSamples) {
        public Parameters {
            if (!Double.isFinite(wavelength) || wavelength <= 0.0
                    || !Double.isFinite(waveSpeed) || waveSpeed <= 0.0
                    || !Double.isFinite(sourceSeparation) || sourceSeparation <= 0.0
                    || !Double.isFinite(amplitude) || amplitude < 0.0
                    || !Double.isFinite(domainSize) || domainSize <= 0.0
                    || sourceSeparation >= domainSize
                    || spatialSamples < MIN_SPATIAL_SAMPLES || spatialSamples > MAX_SPATIAL_SAMPLES) {
                throw new IllegalArgumentException(
                        "Water-surface parameters must be finite, physically valid and use 8 to 128 spatial samples");
            }
        }
    }
}
