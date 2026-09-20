package com.example.backend.physics;

import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.reference.dynamics.AdvancedOscillationReferenceSolver;
import com.example.backend.physics.reference.dynamics.MomentEquilibriumReferenceSolver;
import com.example.backend.physics.reference.medical.MedicalImagingReferenceSolver;
import com.example.backend.physics.reference.modern.QuantumExtensionReferenceSolver;
import com.example.backend.physics.reference.thermal.PhaseChangeReferenceSolver;
import com.example.backend.physics.solver.dynamics.AdvancedOscillationSolver;
import com.example.backend.physics.solver.dynamics.MomentEquilibriumSolver;
import com.example.backend.physics.solver.medical.MedicalImagingSolver;
import com.example.backend.physics.solver.modern.QuantumExtensionSolver;
import com.example.backend.physics.solver.thermal.PhaseChangeSolver;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;

/** Formula and boundary tests for the first official-program gap tranche. */
class OfficialProgramExpansionTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void dampedForcedOscillationMatchesReference() {
        ObjectNode specification = mapper.createObjectNode().put("model", "damped_forced_oscillation")
                .put("mass", 1).put("spring_constant", 4).put("damping_coefficient", .2)
                .put("driving_force_amplitude", 1).put("driving_frequency", 1)
                .put("initial_displacement", .1).put("initial_velocity", 0);
        SolverOutput output = new AdvancedOscillationSolver().solve(specification, Map.of(), 2, .1);
        var checkpoint = new AdvancedOscillationReferenceSolver().solve(specification, Map.of(), 1.3);
        assertEquals(checkpoint.values().get("displacement"), output.values().get("displacement").get(13), 1e-12);
        assertEquals(checkpoint.values().get("velocity"), output.values().get("velocity").get(13), 1e-12);
        var critical = new AdvancedOscillationSolver().solve(
                specification.deepCopy().put("damping_coefficient", 2), Map.of(), 1, .1);
        var over = new AdvancedOscillationSolver().solve(
                specification.deepCopy().put("damping_coefficient", 3), Map.of(), 1, .1);
        assertEquals(0.1, critical.values().get("displacement").get(0), 1e-12);
        assertEquals(0.1, over.values().get("displacement").get(0), 1e-12);
    }

    @Test
    void phaseChangeHasLatentHeatPlateaus() {
        ObjectNode specification = mapper.createObjectNode().put("model", "phase_change")
                .put("mass", 1).put("initial_temperature", 273.15).put("melting_temperature", 273.15)
                .put("boiling_temperature", 373.15).put("specific_heat_solid", 2000)
                .put("specific_heat_liquid", 4000).put("specific_heat_gas", 2000)
                .put("latent_heat_fusion", 334000).put("latent_heat_vaporization", 2256000)
                .put("heating_power", 1000);
        SolverOutput output = new PhaseChangeSolver().solve(specification, Map.of(), 500, 1);
        var reference = new PhaseChangeReferenceSolver().solve(specification, Map.of(), 334);
        assertEquals(273.15, output.values().get("temperature").get(334), 1e-12);
        assertEquals(reference.values().get("liquidFraction"), output.values().get("liquidFraction").get(334), 1e-12);
        assertEquals(1, new PhaseChangeReferenceSolver().solve(specification, Map.of(), 700).values().get("liquidFraction"));
    }

    @Test
    void medicalModelsMatchClosedForm() {
        ObjectNode xray = mapper.createObjectNode().put("model", "xray_imaging")
                .put("incident_intensity", 100).put("attenuation_coefficient", 2)
                .put("material_thickness", .5).put("exposure_time", 2);
        SolverOutput xrayOutput = new MedicalImagingSolver().solve(xray, Map.of(), 1, .25);
        assertEquals(100 * Math.exp(-1), xrayOutput.values().get("transmittedIntensity").get(4), 1e-12);
        assertEquals(xrayOutput.values().get("halfValueLayer").get(0),
                new MedicalImagingReferenceSolver().solve(xray, Map.of(), 0).values().get("halfValueLayer"), 1e-12);

        ObjectNode ct = mapper.createObjectNode().put("model", "ct_reconstruction")
                .put("incident_intensity", 100).put("attenuation_coefficient", .5)
                .put("path_length", 2).put("projection_count", 180);
        assertEquals(1, new MedicalImagingReferenceSolver().solve(ct, Map.of(), 0).values().get("lineIntegral"), 1e-12);

        ObjectNode mri = mapper.createObjectNode().put("model", "mri_relaxation")
                .put("equilibrium_magnetization", 1).put("longitudinal_relaxation_time", 1)
                .put("transverse_relaxation_time", .5).put("echo_time", .5);
        SolverOutput mriOutput = new MedicalImagingSolver().solve(mri, Map.of(), 1, .25);
        assertEquals(1 - Math.exp(-.5), mriOutput.values().get("longitudinalMagnetization").get(2), 1e-12);
    }

    @Test
    void deBroglieDiffractionMatchesReference() {
        ObjectNode specification = mapper.createObjectNode().put("model", "de_broglie_diffraction")
                .put("particle_mass", 9.1093837e-31).put("particle_speed", 1e6)
                .put("lattice_spacing", 1e-9).put("diffraction_order", 1);
        SolverOutput output = new QuantumExtensionSolver().solve(specification, Map.of(), 1, .1);
        var reference = new QuantumExtensionReferenceSolver().solve(specification, Map.of(), 0);
        assertEquals(reference.values().get("wavelength"), output.values().get("wavelength").get(0), 1e-18);
        assertEquals(reference.values().get("diffractionAngle"), output.values().get("diffractionAngle").get(0), 1e-12);

        ObjectNode unavailable = specification.deepCopy().put("lattice_spacing", 1e-12);
        var unavailableOutput = new QuantumExtensionSolver().solve(unavailable, Map.of(), 1, .1);
        assertEquals(0, unavailableOutput.values().get("diffractionAngle").get(0), 1e-12);
        assertEquals(0, unavailableOutput.values().get("diffractionAllowed").get(0), 1e-12);
    }

    @Test
    void electronicsAndRadioChainMatchReference() {
        ObjectNode sensor = mapper.createObjectNode().put("model", "sensor_op_amp")
                .put("supply_voltage", 5).put("sensor_resistance", 1000)
                .put("reference_resistance", 1000).put("op_amp_gain", 10).put("threshold_voltage", 2);
        SolverOutput sensorOutput = new com.example.backend.physics.solver.electromagnetism.ApplicationsSolver()
                .solve(sensor, Map.of(), 1, .25);
        assertEquals(2.5, sensorOutput.values().get("sensorVoltage").get(0), 1e-12);
        assertEquals(sensorOutput.values().get("ledState").get(0),
                new com.example.backend.physics.reference.electromagnetism.ApplicationsReferenceSolver()
                        .solve(sensor, Map.of(), 0).values().get("ledState"), 1e-12);

        ObjectNode radio = mapper.createObjectNode().put("model", "radio_signal_chain")
                .put("carrier_frequency", 1e8).put("modulation_frequency", 1e4)
                .put("frequency_deviation", 2e4).put("modulation_index", .5)
                .put("signal_amplitude", 10).put("path_length", 100)
                .put("attenuation_db_per_meter", .01);
        var radioOutput = new com.example.backend.physics.solver.electromagnetism.ApplicationsSolver()
                .solve(radio, Map.of(), 1, .25);
        assertEquals(2, radioOutput.values().get("fmModulationIndex").get(0), 1e-12);
        assertEquals(radioOutput.values().get("receivedAmplitude").get(0),
                new com.example.backend.physics.reference.electromagnetism.ApplicationsReferenceSolver()
                        .solve(radio, Map.of(), 0).values().get("receivedAmplitude"), 1e-12);
    }

    @Test
    void momentBalanceUsesPerpendicularComponents() {
        ObjectNode moment = mapper.createObjectNode().put("model", "moment_equilibrium")
                .put("force_1", 10).put("arm_1", 2).put("angle_1", Math.PI / 2)
                .put("force_2", 5).put("arm_2", 4).put("angle_2", -Math.PI / 2);
        SolverOutput output = new MomentEquilibriumSolver().solve(moment, Map.of(), 1, .1);
        assertEquals(0, output.values().get("netMoment").get(0), 1e-12);
        assertEquals(output.values().get("equilibriumResidual").get(0),
                new MomentEquilibriumReferenceSolver().solve(moment, Map.of(), 0).values().get("equilibriumResidual"), 1e-12);
    }

    @Test
    void sourceEnergyBandsAndNuclearReactionMatchReferences() {
        ObjectNode source = mapper.createObjectNode().put("model", "source_internal_resistance")
                .put("emf", 12).put("internal_resistance", 1).put("load_resistance", 5);
        var sourceOutput = new com.example.backend.physics.solver.circuits.SourceInternalResistanceSolver()
                .solve(source, Map.of(), 1, .1);
        assertEquals(2, sourceOutput.values().get("current").get(0), 1e-12);
        assertEquals(sourceOutput.values().get("terminalVoltage").get(0),
                new com.example.backend.physics.reference.circuits.SourceInternalResistanceReferenceSolver()
                        .solve(source, Map.of(), 0).values().get("terminalVoltage"), 1e-12);

        ObjectNode bands = mapper.createObjectNode().put("model", "energy_band_transition")
                .put("valence_band_energy", 0).put("conduction_band_energy", 3e-19)
                .put("photon_frequency", 1e15);
        var bandOutput = new com.example.backend.physics.solver.modern.EnergyBandSolver()
                .solve(bands, Map.of(), 1, .1);
        assertEquals(3e-19, bandOutput.values().get("bandGap").get(0), 1e-30);
        assertEquals(bandOutput.values().get("transitionAllowed").get(0),
                new com.example.backend.physics.reference.modern.EnergyBandReferenceSolver()
                        .solve(bands, Map.of(), 0).values().get("transitionAllowed"), 1e-12);

        ObjectNode reaction = mapper.createObjectNode().put("model", "nuclear_reaction_energy")
                .put("reactant_mass", 5e-27).put("product_mass", 4.99e-27).put("reaction_count", 2);
        var reactionOutput = new com.example.backend.physics.solver.modern.NuclearReactionSolver()
                .solve(reaction, Map.of(), 1, .1);
        assertEquals(reactionOutput.values().get("totalReleasedEnergy").get(0),
                new com.example.backend.physics.reference.modern.NuclearReactionReferenceSolver()
                        .solve(reaction, Map.of(), 0).values().get("totalReleasedEnergy"), 1e-12);
    }

    @Test
    void astronomyAndEnvironmentModelsMatchReference() {
        ObjectNode eclipse = mapper.createObjectNode().put("model", "eclipse_geometry")
                .put("star_radius", 1).put("star_distance", 100)
                .put("occluder_radius", 1).put("occluder_distance", 100)
                .put("alignment_angle", 0);
        var eclipseOutput = new com.example.backend.physics.solver.electromagnetism.ApplicationsSolver()
                .solve(eclipse, Map.of(), 1, .1);
        assertEquals(1, eclipseOutput.values().get("totality").get(0), 1e-12);
        assertEquals(eclipseOutput.values().get("alignmentMargin").get(0),
                new com.example.backend.physics.reference.electromagnetism.ApplicationsReferenceSolver()
                        .solve(eclipse, Map.of(), 0).values().get("alignmentMargin"), 1e-12);

        ObjectNode environment = mapper.createObjectNode().put("model", "energy_environment")
                .put("energy_demand", 100).put("renewable_fraction", .4)
                .put("fossil_emission_factor", 2).put("renewable_emission_factor", .1)
                .put("conversion_efficiency", .8);
        var environmentOutput = new com.example.backend.physics.solver.electromagnetism.ApplicationsSolver()
                .solve(environment, Map.of(), 1, .1);
        assertEquals(40, environmentOutput.values().get("renewableEnergy").get(0), 1e-12);
        assertEquals(environmentOutput.values().get("emissions").get(0),
                new com.example.backend.physics.reference.electromagnetism.ApplicationsReferenceSolver()
                        .solve(environment, Map.of(), 0).values().get("emissions"), 1e-12);
    }

    @Test
    void dragUniformFieldAndThermistorModelsMatchReferences() {
        ObjectNode drag = mapper.createObjectNode().put("model", "linear_drag_motion")
                .put("mass", 2).put("initial_position", 0).put("initial_velocity", 0)
                .put("constant_force", 10).put("drag_coefficient", 2);
        var dragOutput = new com.example.backend.physics.solver.dynamics.LinearDragSolver()
                .solve(drag, Map.of(), 2, .1);
        assertEquals(5 * (1 - Math.exp(-2)), dragOutput.values().get("velocity").get(20), 1e-12);
        assertEquals(dragOutput.values().get("position").get(13),
                new com.example.backend.physics.reference.dynamics.LinearDragReferenceSolver()
                        .solve(drag, Map.of(), 1.3).values().get("position"), 1e-12);

        ObjectNode field = mapper.createObjectNode().put("model", "uniform_electric_field")
                .put("charge", 2).put("mass", 4).put("potential_difference", 10)
                .put("plate_separation", 2).put("initial_velocity", 3).put("travel_time", 2);
        var fieldOutput = new com.example.backend.physics.solver.electromagnetism.UniformElectricFieldSolver()
                .solve(field, Map.of(), 1, .1);
        assertEquals(5, fieldOutput.values().get("fieldStrength").get(0), 1e-12);
        assertEquals(fieldOutput.values().get("transverseDisplacement").get(0),
                new com.example.backend.physics.reference.electromagnetism.UniformElectricFieldReferenceSolver()
                        .solve(field, Map.of(), 0).values().get("transverseDisplacement"), 1e-12);

        ObjectNode thermistor = mapper.createObjectNode().put("model", "thermistor_response")
                .put("reference_resistance", 10000).put("reference_temperature", 298.15)
                .put("beta_constant", 3950).put("temperature", 25).put("supply_voltage", 5)
                .put("divider_resistance", 10000);
        var thermistorOutput = new com.example.backend.physics.solver.electromagnetism.ApplicationsSolver()
                .solve(thermistor, Map.of(), 1, .1);
        assertEquals(10000, thermistorOutput.values().get("resistance").get(0), 1e-9);
    }
}
