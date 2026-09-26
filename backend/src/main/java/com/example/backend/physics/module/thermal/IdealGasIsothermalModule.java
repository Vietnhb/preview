package com.example.backend.physics.module.thermal;

import com.example.backend.physics.model.AnalyticalPoint;
import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.module.PhysicsModule;
import com.example.backend.physics.module.SimulationClock;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/** Typed numerical and independent reference paths for an isothermal ideal-gas process. */
public final class IdealGasIsothermalModule implements PhysicsModule<IdealGasIsothermalModule.Parameters> {
    public static final String MODULE_ID = "ideal_gas_isothermal";
    public static final String NUMERICAL_SOLVER_ID = "ideal_gas_isothermal_solver_v2";
    public static final String REFERENCE_SOLVER_ID = "ideal_gas_isothermal_reference_v2";
    private static final String TEMPERATURE = "temperature";
    private static final String VOLUME = "volume";
    private static final String PRESSURE = "pressure";

    @Override public String moduleId() { return MODULE_ID; }
    @Override public String numericalSolverId() { return NUMERICAL_SOLVER_ID; }
    @Override public String referenceSolverId() { return REFERENCE_SOLVER_ID; }

    @Override
    public Parameters bind(CanonicalQuantityBag quantities) {
        if (quantities == null) {
            throw new IllegalArgumentException("Canonical isothermal ideal-gas quantities are required");
        }
        requireUnit(quantities, "amount_of_substance", "mol");
        requireUnit(quantities, TEMPERATURE, "K");
        requireUnit(quantities, "initial_volume", "m3");
        requireUnit(quantities, "volume_rate", "m3/s");
        requireUnit(quantities, "gas_constant", "J/(mol*K)");
        return new Parameters(
                quantities.require("amount_of_substance"),
                quantities.require(TEMPERATURE),
                quantities.require("initial_volume"),
                quantities.require("volume_rate"),
                quantities.require("gas_constant"));
    }

    @Override
    public SolverOutput solve(Parameters parameters, SimulationClock clock) {
        requireParameters(parameters);
        if (clock == null) {
            throw new IllegalArgumentException("Isothermal ideal-gas simulation clock is required");
        }

        double pressureScale = parameters.amountOfSubstance() * parameters.gasConstant()
                * parameters.temperature();
        requirePositiveFinite("nRT", pressureScale);

        List<Double> time = clock.sampleTimes();
        List<Double> volume = new ArrayList<>(time.size());
        List<Double> pressure = new ArrayList<>(time.size());
        List<Double> temperature = new ArrayList<>(time.size());
        List<Double> work = new ArrayList<>(time.size());
        for (double currentTime : time) {
            double currentVolume = parameters.initialVolume()
                    + parameters.volumeRate() * currentTime;
            requirePositiveFinite(VOLUME, currentVolume);

            // The numerical path evaluates P = nRT/V and uses log1p to retain
            // precision when the volume changes only slightly.
            double currentPressure = pressureScale / currentVolume;
            double relativeVolumeChange = (currentVolume - parameters.initialVolume())
                    / parameters.initialVolume();
            double logVolumeRatio = Double.isFinite(relativeVolumeChange) && relativeVolumeChange > -1.0
                    ? Math.log1p(relativeVolumeChange)
                    : Math.log(currentVolume) - Math.log(parameters.initialVolume());
            double workByGas = pressureScale * logVolumeRatio;
            requirePositiveFinite(PRESSURE, currentPressure);
            requireFinite("work", workByGas);

            volume.add(currentVolume);
            pressure.add(currentPressure);
            temperature.add(parameters.temperature());
            work.add(workByGas);
        }

        Map<String, List<Double>> values = new LinkedHashMap<>();
        values.put(VOLUME, volume);
        values.put(PRESSURE, pressure);
        values.put(TEMPERATURE, temperature);
        values.put("work", work);
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }

    @Override
    public AnalyticalPoint referenceAt(Parameters parameters, double timeSeconds) {
        requireParameters(parameters);
        if (!Double.isFinite(timeSeconds) || timeSeconds < 0.0) {
            throw new IllegalArgumentException("Reference time must be finite and non-negative");
        }

        double volume = parameters.initialVolume() + parameters.volumeRate() * timeSeconds;
        requirePositiveFinite("reference " + VOLUME, volume);

        // The oracle evaluates the ideal-gas law in log space and obtains work
        // as a difference of logarithms. It does not reuse the numerical
        // solver's direct division or log1p relative-volume calculation.
        double logPressure = Math.log(parameters.amountOfSubstance())
                + Math.log(parameters.gasConstant())
                + Math.log(parameters.temperature())
                - Math.log(volume);
        double pressure = Math.exp(logPressure);
        double pressureScale = parameters.amountOfSubstance() * parameters.gasConstant()
                * parameters.temperature();
        double workByGas = pressureScale
                * (Math.log(volume) - Math.log(parameters.initialVolume()));
        requirePositiveFinite("reference " + PRESSURE, pressure);
        requireFinite("reference work", workByGas);

        return new AnalyticalPoint(Map.of(
                VOLUME, volume,
                PRESSURE, pressure,
                TEMPERATURE, parameters.temperature(),
                "work", workByGas));
    }

    private static void requireUnit(CanonicalQuantityBag quantities, String key, String expectedUnit) {
        if (!expectedUnit.equals(quantities.unit(key))) {
            throw new IllegalArgumentException("Canonical unit for '" + key + "' must be " + expectedUnit);
        }
    }

    private static void requireParameters(Parameters parameters) {
        if (parameters == null) {
            throw new IllegalArgumentException("Isothermal ideal-gas parameters are required");
        }
    }

    private static void requireFinite(String outputKey, double value) {
        if (!Double.isFinite(value)) {
            throw new IllegalArgumentException("Isothermal ideal-gas process produced non-finite " + outputKey);
        }
    }

    private static void requirePositiveFinite(String outputKey, double value) {
        requireFinite(outputKey, value);
        if (value <= 0.0) {
            throw new IllegalArgumentException("Isothermal ideal-gas process produced non-positive " + outputKey);
        }
    }

    /** Immutable primitive state bound once from canonical quantities. */
    public record Parameters(double amountOfSubstance, double temperature,
                             double initialVolume, double volumeRate, double gasConstant) {
        public Parameters {
            if (!Double.isFinite(amountOfSubstance) || amountOfSubstance <= 0.0
                    || !Double.isFinite(temperature) || temperature <= 0.0
                    || !Double.isFinite(initialVolume) || initialVolume <= 0.0
                    || !Double.isFinite(volumeRate)
                    || !Double.isFinite(gasConstant) || gasConstant <= 0.0) {
                throw new IllegalArgumentException("Isothermal ideal gas requires positive finite amount, temperature, "
                        + "initial volume and gas constant, and a finite volume rate");
            }
        }
    }
}
