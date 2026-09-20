package com.example.backend.physics;

import com.example.backend.physics.solver.*;
import com.example.backend.physics.solver.kinematics.*;
import com.example.backend.physics.solver.circuits.*;
import com.example.backend.physics.solver.modern.*;
import com.example.backend.physics.solver.electromagnetism.*;
import com.example.backend.physics.solver.dynamics.*;
import com.example.backend.physics.solver.optics.*;
import com.example.backend.physics.solver.thermal.*;
import com.example.backend.physics.solver.waves.*;
import com.example.backend.physics.solver.practical.*;
import com.example.backend.physics.reference.*;
import com.example.backend.physics.reference.kinematics.*;
import com.example.backend.physics.reference.circuits.*;
import com.example.backend.physics.reference.modern.*;
import com.example.backend.physics.reference.electromagnetism.*;
import com.example.backend.physics.reference.dynamics.*;
import com.example.backend.physics.reference.optics.*;
import com.example.backend.physics.reference.thermal.*;
import com.example.backend.physics.reference.waves.*;
import com.example.backend.physics.reference.practical.*;
import com.example.backend.physics.model.SolverOutput;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/** Golden checks for curriculum families that were previously only catalog gaps. */
class CurriculumMissingFamiliesTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private ObjectNode model(String name) { return mapper.createObjectNode().put("model", name); }

    @Test void temperatureAndExpansion() {
        ObjectNode scales = model("temperature_scales").put("temperature_celsius", 25);
        SolverOutput s = new TemperatureScaleSolver().solve(scales, Map.of(), 1, .25);
        assertEquals(298.15, s.values().get("kelvin").get(4), 1e-12);
        assertEquals(77, s.values().get("fahrenheit").get(4), 1e-12);
        ObjectNode expansion = model("thermal_expansion").put("initial_length", 2).put("linear_expansion_coefficient", 1e-5).put("initial_temperature", 300).put("final_temperature", 400);
        assertEquals(2.002, new ThermalExpansionSolver().solve(expansion, Map.of(), 1, .25).values().get("finalLength").get(4), 1e-12);
    }

    @Test void dcNetworksAndCapacitor() {
        ObjectNode ohm = model("ohms_law").put("voltage", 12).put("resistance", 4);
        assertEquals(3, new OhmsLawSolver().solve(ohm, Map.of(), 1, .25).values().get("current").get(4), 1e-12);
        ObjectNode series = model("resistors_series").put("voltage", 12).put("resistance_1", 2).put("resistance_2", 4);
        assertEquals(6, new ResistorNetworkSolver().solve(series, Map.of(), 1, .25).values().get("equivalentResistance").get(4), 1e-12);
        ObjectNode parallel = model("resistors_parallel").put("voltage", 12).put("resistance_1", 2).put("resistance_2", 4);
        assertEquals(9, new ResistorNetworkReferenceSolver().solve(parallel, Map.of(), .5).values().get("totalCurrent"), 1e-12);
        ObjectNode capacitor = model("capacitor_basic").put("capacitance", .5).put("voltage", 4);
        assertEquals(2, new CapacitorSolver().solve(capacitor, Map.of(), 1, .25).values().get("charge").get(4), 1e-12);
        assertEquals(4, new CapacitorReferenceSolver().solve(capacitor, Map.of(), .5).values().get("energy"), 1e-12);
    }

    @Test void acTransformerAndGasProcesses() {
        ObjectNode ac = model("ac_waveform").put("peak_voltage", 10).put("frequency", 50).put("phase", Math.PI / 2);
        assertEquals(10, new AcWaveformSolver().solve(ac, Map.of(), .01, .002).values().get("voltage").get(0), 1e-12);
        ObjectNode transformer = model("ideal_transformer").put("primary_turns", 100).put("secondary_turns", 200).put("primary_voltage", 12).put("secondary_current", 3);
        assertEquals(24, new TransformerSolver().solve(transformer, Map.of(), 1, .25).values().get("secondaryVoltage").get(4), 1e-12);
        ObjectNode gas = model("adiabatic_gas").put("initial_pressure", 100000).put("initial_volume", 1).put("final_volume", 2).put("initial_temperature", 300).put("heat_capacity_ratio", 1.4);
        assertEquals(100000 * Math.pow(.5, 1.4), new AdiabaticGasReferenceSolver().solve(gas, Map.of(), .5).values().get("finalPressure"), 1e-9);
    }

    @Test void opticalAndModernFamilies() {
        ObjectNode interference = model("light_interference").put("wavelength", 500e-9).put("path_difference", 0).put("reference_intensity", 2);
        assertEquals(2, new LightInterferenceSolver().solve(interference, Map.of(), 1, .25).values().get("intensity").get(4), 1e-12);
        ObjectNode diffraction = model("diffraction_polarization").put("wavelength", 500e-9).put("slit_width", 1e-6).put("diffraction_order", 1).put("input_intensity", 4).put("analyzer_angle", Math.PI / 3);
        assertEquals(Math.PI / 6, new DiffractionSolver().solve(diffraction, Map.of(), 1, .25).values().get("diffractionAngle").get(4), 1e-12);
        ObjectNode spectrum = model("atomic_spectra").put("initial_level", 3).put("final_level", 2);
        assertEquals(new HydrogenSpectrumReferenceSolver().solve(spectrum, Map.of(), .5).values().get("wavelength"), new HydrogenSpectrumSolver().solve(spectrum, Map.of(), 1, .25).values().get("wavelength").get(4), 1e-15);
        ObjectNode safety = model("radiation_safety").put("reference_dose_rate", 4).put("reference_distance", 1).put("distance", 2);
        assertEquals(1, new RadiationSafetySolver().solve(safety, Map.of(), 1, .25).values().get("doseRate").get(4), 1e-12);
        ObjectNode magnifier = model("simple_magnifier").put("focal_length", .1).put("object_distance", .05).put("near_point", .25);
        assertEquals(.1, new OpticalInstrumentSolver().solve(magnifier, Map.of(), 1, .25).values().get("virtualImageDistance").get(4), 1e-12);
        ObjectNode microscope = model("compound_microscope").put("objective_focal_length", .005).put("eyepiece_focal_length", .025).put("tube_length", .16).put("near_point", .25);
        assertEquals(352, new OpticalInstrumentReferenceSolver().solve(microscope, Map.of(), .5).values().get("nearPointAngularMagnification"), 1e-12);
        ObjectNode telescope = model("astronomical_telescope").put("objective_focal_length", 1.2).put("eyepiece_focal_length", .03);
        assertEquals(40, new OpticalInstrumentSolver().solve(telescope, Map.of(), 1, .25).values().get("angularMagnification").get(4), 1e-12);
        ObjectNode water = model("water_surface_interference").put("wavelength", .01).put("wave_speed", .1)
                .put("source_separation", .01).put("amplitude", .1).put("domain_size", .04).put("spatial_samples", 9);
        SolverOutput waterOutput = new WaterSurfaceInterferenceSolver().solve(water, Map.of(), 1, .25);
        assertEquals(-.2, waterOutput.scalarFields().get("waterSurface").values().getFirst().get(40), 1e-12);
        assertEquals(-.2, new WaterSurfaceInterferenceReferenceSolver().solve(water, Map.of(), 0).values().get("centerHeight"), 1e-12);
        double checkpoint = waterOutput.time().get(2);
        assertEquals(new WaterSurfaceInterferenceReferenceSolver().solve(water, Map.of(), checkpoint).values().get("centerHeight"),
                waterOutput.values().get("centerHeight").get(2), 1e-12);
    }

    @Test void mechanicsFoundationFamilies() {
        ObjectNode work = model("work_energy_power").put("mass", 2).put("initial_speed", 3).put("final_speed", 5)
                .put("force", 10).put("displacement", 4).put("force_angle", 0).put("duration", 2);
        var workOutput = new MechanicsFoundationSolver().solve(work, Map.of(), 1, .25);
        assertEquals(40, workOutput.values().get("workByForce").get(4), 1e-12);
        assertEquals(new MechanicsFoundationReferenceSolver().solve(work, Map.of(), .5).values().get("deltaKineticEnergy"), workOutput.values().get("deltaKineticEnergy").get(4), 1e-12);

        ObjectNode circular = model("circular_motion").put("mass", 2).put("radius", 4).put("speed", 6);
        assertEquals(18, new MechanicsFoundationSolver().solve(circular, Map.of(), 1, .25).values().get("centripetalForce").get(4), 1e-12);
        ObjectNode hooke = model("hooke_law").put("spring_constant", 100).put("displacement", -.2);
        assertEquals(20, new MechanicsFoundationReferenceSolver().solve(hooke, Map.of(), 0).values().get("restoringForce"), 1e-12);

        ObjectNode orbit = model("gravity_orbit").put("central_mass", 5.972e24).put("satellite_mass", 1000).put("orbit_radius", 6.771e6);
        double expectedField = 6.67430e-11 * 5.972e24 / (6.771e6 * 6.771e6);
        assertEquals(expectedField, new MechanicsFoundationSolver().solve(orbit, Map.of(), 1, .25).values().get("gravitationalField").get(4), 1e-12);
        ObjectNode hydro = model("hydrostatics").put("fluid_density", 1000).put("depth", 3).put("displaced_volume", .02);
        assertEquals(29430, new MechanicsFoundationReferenceSolver().solve(hydro, Map.of(), 0).values().get("gaugePressure"), 1e-9);
        assertThrows(IllegalArgumentException.class, () -> new MechanicsFoundationSolver().solve(
                model("circular_motion").put("mass", 1).put("radius", 1).put("speed", 0), Map.of(), 1, .1));
    }

    @Test void communicationsElectronicsAndMedicalFamilies() {
        ObjectNode radio = model("radio_communication").put("carrier_frequency", 1e8).put("modulation_frequency", 1e4).put("modulation_index", .5);
        assertEquals(2.99792458, new ApplicationsSolver().solve(radio, Map.of(), 1, .25).values().get("wavelength").get(4), 1e-10);
        ObjectNode diode = model("diode_characteristic").put("voltage", .025851999786).put("saturation_current", 1e-12).put("ideality_factor", 1).put("temperature", 300);
        assertEquals(Math.expm1(1) * 1e-12, new ApplicationsReferenceSolver().solve(diode, Map.of(), 0).values().get("current"), 1e-15);
        ObjectNode ultrasound = model("ultrasound_imaging").put("sound_speed", 1540).put("frequency", 5e6).put("echo_time", 130e-6);
        assertEquals(.1001, new ApplicationsSolver().solve(ultrasound, Map.of(), 1, .25).values().get("depth").get(4), 1e-12);
        assertThrows(IllegalArgumentException.class, () -> new ApplicationsSolver().solve(
                model("diode_characteristic").put("voltage", .7).put("saturation_current", 1e-12).put("ideality_factor", 1).put("temperature", 0), Map.of(), 1, .1));
    }

    @Test void idealGasProcessVariants() {
        ObjectNode isobaric = model("ideal_gas_isobaric").put("initial_pressure", 100000).put("initial_volume", 1)
                .put("initial_temperature", 300).put("final_temperature", 600);
        var isobaricOutput = new GasProcessSolver().solve(isobaric, Map.of(), 1, .25);
        assertEquals(2, isobaricOutput.values().get("finalVolume").get(4), 1e-12);
        assertEquals(100000, isobaricOutput.values().get("finalPressure").get(4), 1e-12);
        assertEquals(new GasProcessReferenceSolver().solve(isobaric, Map.of(), 0).values().get("work"), isobaricOutput.values().get("work").get(4), 1e-12);
        ObjectNode isochoric = model("ideal_gas_isochoric").put("initial_pressure", 100000).put("initial_volume", 1)
                .put("initial_temperature", 300).put("final_temperature", 600);
        var isochoricOutput = new GasProcessSolver().solve(isochoric, Map.of(), 1, .25);
        assertEquals(200000, isochoricOutput.values().get("finalPressure").get(4), 1e-12);
        assertEquals(0, isochoricOutput.values().get("work").get(4), 1e-12);
    }
}
