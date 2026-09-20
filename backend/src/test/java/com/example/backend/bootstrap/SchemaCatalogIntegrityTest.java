package com.example.backend.bootstrap;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.example.backend.service.problem.SchemaCompiler;
import com.example.backend.physics.binding.CanonicalQuantityCompiler;
import com.example.backend.physics.module.PhysicsModuleConfiguration;
import com.example.backend.physics.module.circuits.DiodeCharacteristicModule;
import com.example.backend.physics.module.circuits.AcRlcCircuitModule;
import com.example.backend.physics.module.circuits.SensorOpAmpModule;
import com.example.backend.physics.module.circuits.ThermistorResponseModule;
import com.example.backend.physics.module.circuits.RcChargingModule;
import com.example.backend.physics.module.circuits.RcDischargingModule;
import com.example.backend.physics.module.circuits.ResistorsParallelModule;
import com.example.backend.physics.module.circuits.ResistorsSeriesModule;
import com.example.backend.physics.module.dynamics.CircularMotionModule;
import com.example.backend.physics.module.dynamics.DynamicsCollisionModule;
import com.example.backend.physics.module.dynamics.DynamicsForcesModule;
import com.example.backend.physics.module.dynamics.LinearDragMotionModule;
import com.example.backend.physics.module.dynamics.MomentEquilibriumModule;
import com.example.backend.physics.module.dynamics.SpringOscillationModule;
import com.example.backend.physics.module.dynamics.UniformAccelerationModule;
import com.example.backend.physics.module.electromagnetism.UniformElectricFieldModule;
import com.example.backend.physics.module.electromagnetism.MagneticForceModule;
import com.example.backend.physics.module.electromagnetism.PointChargeFieldModule;
import com.example.backend.physics.module.electromagnetism.ElectromagneticInductionModule;
import com.example.backend.physics.module.dynamics.WorkEnergyPowerModule;
import com.example.backend.physics.module.kinematics.KinematicsProjectileModule;
import com.example.backend.physics.module.kinematics.AccelerationTimeGraphModule;
import com.example.backend.physics.module.kinematics.PositionTimeGraphModule;
import com.example.backend.physics.module.kinematics.VelocityTimeGraphModule;
import com.example.backend.physics.module.optics.DiffractionPolarizationModule;
import com.example.backend.physics.module.optics.LightInterferenceModule;
import com.example.backend.physics.module.optics.AstronomicalTelescopeModule;
import com.example.backend.physics.module.optics.CompoundMicroscopeModule;
import com.example.backend.physics.module.optics.SimpleMagnifierModule;
import com.example.backend.physics.module.modern.PhotoelectricEffectModule;
import com.example.backend.physics.module.modern.RadioactiveDecayModule;
import com.example.backend.physics.module.modern.NuclearEnergyModule;
import com.example.backend.physics.module.modern.RadiationSafetyModule;
import com.example.backend.physics.module.modern.CtReconstructionModule;
import com.example.backend.physics.module.modern.DeBroglieDiffractionModule;
import com.example.backend.physics.module.modern.AtomicSpectraModule;
import com.example.backend.physics.module.modern.EclipseGeometryModule;
import com.example.backend.physics.module.modern.EnergyBandTransitionModule;
import com.example.backend.physics.module.modern.MriRelaxationModule;
import com.example.backend.physics.module.modern.NuclearReactionEnergyModule;
import com.example.backend.physics.module.modern.XrayImagingModule;
import com.example.backend.physics.module.practical.MeasurementUncertaintyModule;
import com.example.backend.physics.module.practical.EnergyEnvironmentModule;
import com.example.backend.physics.module.practical.ExperimentalDataGraphModule;
import com.example.backend.physics.module.thermal.AdiabaticGasModule;
import com.example.backend.physics.module.thermal.IdealGasIsochoricModule;
import com.example.backend.physics.module.thermal.IdealGasIsothermalModule;
import com.example.backend.physics.module.thermal.IdealGasIsobaricModule;
import com.example.backend.physics.module.thermal.FirstLawThermodynamicsModule;
import com.example.backend.physics.module.thermal.CalorimetryMixingModule;
import com.example.backend.physics.module.thermal.ThermalExpansionModule;
import com.example.backend.physics.module.thermal.TemperatureScalesModule;
import com.example.backend.physics.module.thermal.PhaseChangeModule;
import com.example.backend.physics.module.waves.SoundWaveModule;
import com.example.backend.physics.module.waves.StringWaveModule;
import com.example.backend.physics.module.waves.StandingWaveModule;
import com.example.backend.physics.module.waves.WavePulseModule;
import com.example.backend.physics.module.waves.WaveReflectionModule;
import com.example.backend.physics.module.waves.WaveSuperpositionModule;
import com.example.backend.physics.module.waves.WaterSurfaceInterferenceModule;
import com.example.backend.physics.module.waves.RadioCommunicationModule;
import com.example.backend.physics.module.waves.RadioSignalChainModule;
import com.example.backend.physics.module.waves.UltrasoundImagingModule;
import com.example.backend.physics.reference.ReferenceSolverRegistry;
import com.example.backend.physics.solver.PhysicsSolverRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;
import com.example.backend.ai.normalization.UnitNormalizer;

import java.io.InputStream;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class SchemaCatalogIntegrityTest {
    private final ObjectMapper objectMapper = new ObjectMapper();

    @Test
    void typedCatalogBindingsAreAcceptedWithoutLegacySolverRegistration() {
        var typedModules = new PhysicsModuleConfiguration().physicsModuleRegistry();
        var emptyNumericalRegistry = new PhysicsSolverRegistry(java.util.List.of());
        var emptyReferenceRegistry = new ReferenceSolverRegistry(java.util.List.of());

        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> PhysicsCatalogInitializer.validateSolverBinding(
                "hooke_law_solver", "hooke_law_reference", typedModules,
                emptyNumericalRegistry, emptyReferenceRegistry));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> PhysicsCatalogInitializer.validateSolverBinding(
                "hydrostatics_solver", "hydrostatics_reference", typedModules,
                emptyNumericalRegistry, emptyReferenceRegistry));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> PhysicsCatalogInitializer.validateSolverBinding(
                "gravity_orbit_solver", "gravity_orbit_reference", typedModules,
                emptyNumericalRegistry, emptyReferenceRegistry));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> PhysicsCatalogInitializer.validateSolverBinding(
                ResistorsSeriesModule.NUMERICAL_SOLVER_ID, ResistorsSeriesModule.REFERENCE_SOLVER_ID, typedModules,
                emptyNumericalRegistry, emptyReferenceRegistry));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> PhysicsCatalogInitializer.validateSolverBinding(
                ResistorsParallelModule.NUMERICAL_SOLVER_ID, ResistorsParallelModule.REFERENCE_SOLVER_ID, typedModules,
                emptyNumericalRegistry, emptyReferenceRegistry));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> PhysicsCatalogInitializer.validateSolverBinding(
                DiodeCharacteristicModule.NUMERICAL_SOLVER_ID, DiodeCharacteristicModule.REFERENCE_SOLVER_ID,
                typedModules, emptyNumericalRegistry, emptyReferenceRegistry));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> PhysicsCatalogInitializer.validateSolverBinding(
                CircularMotionModule.NUMERICAL_SOLVER_ID, CircularMotionModule.REFERENCE_SOLVER_ID, typedModules,
                emptyNumericalRegistry, emptyReferenceRegistry));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> PhysicsCatalogInitializer.validateSolverBinding(
                AdiabaticGasModule.NUMERICAL_SOLVER_ID, AdiabaticGasModule.REFERENCE_SOLVER_ID, typedModules,
                emptyNumericalRegistry, emptyReferenceRegistry));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> PhysicsCatalogInitializer.validateSolverBinding(
                ThermalExpansionModule.NUMERICAL_SOLVER_ID, ThermalExpansionModule.REFERENCE_SOLVER_ID, typedModules,
                emptyNumericalRegistry, emptyReferenceRegistry));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> PhysicsCatalogInitializer.validateSolverBinding(
                WorkEnergyPowerModule.NUMERICAL_SOLVER_ID, WorkEnergyPowerModule.REFERENCE_SOLVER_ID, typedModules,
                emptyNumericalRegistry, emptyReferenceRegistry));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> PhysicsCatalogInitializer.validateSolverBinding(
                IdealGasIsochoricModule.NUMERICAL_SOLVER_ID, IdealGasIsochoricModule.REFERENCE_SOLVER_ID,
                typedModules, emptyNumericalRegistry, emptyReferenceRegistry));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> PhysicsCatalogInitializer.validateSolverBinding(
                RcChargingModule.NUMERICAL_SOLVER_ID, RcChargingModule.REFERENCE_SOLVER_ID, typedModules,
                emptyNumericalRegistry, emptyReferenceRegistry));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> PhysicsCatalogInitializer.validateSolverBinding(
                RcDischargingModule.NUMERICAL_SOLVER_ID, RcDischargingModule.REFERENCE_SOLVER_ID, typedModules,
                emptyNumericalRegistry, emptyReferenceRegistry));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> PhysicsCatalogInitializer.validateSolverBinding(
                IdealGasIsothermalModule.NUMERICAL_SOLVER_ID, IdealGasIsothermalModule.REFERENCE_SOLVER_ID,
                typedModules, emptyNumericalRegistry, emptyReferenceRegistry));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> PhysicsCatalogInitializer.validateSolverBinding(
                LinearDragMotionModule.NUMERICAL_SOLVER_ID, LinearDragMotionModule.REFERENCE_SOLVER_ID, typedModules,
                emptyNumericalRegistry, emptyReferenceRegistry));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> PhysicsCatalogInitializer.validateSolverBinding(
                UniformAccelerationModule.NUMERICAL_SOLVER_ID, UniformAccelerationModule.REFERENCE_SOLVER_ID,
                typedModules, emptyNumericalRegistry, emptyReferenceRegistry));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> PhysicsCatalogInitializer.validateSolverBinding(
                UniformElectricFieldModule.NUMERICAL_SOLVER_ID, UniformElectricFieldModule.REFERENCE_SOLVER_ID,
                typedModules, emptyNumericalRegistry, emptyReferenceRegistry));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> PhysicsCatalogInitializer.validateSolverBinding(
                IdealGasIsobaricModule.NUMERICAL_SOLVER_ID, IdealGasIsobaricModule.REFERENCE_SOLVER_ID,
                typedModules, emptyNumericalRegistry, emptyReferenceRegistry));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> PhysicsCatalogInitializer.validateSolverBinding(
                TemperatureScalesModule.NUMERICAL_SOLVER_ID, TemperatureScalesModule.REFERENCE_SOLVER_ID,
                typedModules, emptyNumericalRegistry, emptyReferenceRegistry));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> PhysicsCatalogInitializer.validateSolverBinding(
                PhotoelectricEffectModule.NUMERICAL_SOLVER_ID, PhotoelectricEffectModule.REFERENCE_SOLVER_ID,
                typedModules, emptyNumericalRegistry, emptyReferenceRegistry));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> PhysicsCatalogInitializer.validateSolverBinding(
                SoundWaveModule.NUMERICAL_SOLVER_ID, SoundWaveModule.REFERENCE_SOLVER_ID,
                typedModules, emptyNumericalRegistry, emptyReferenceRegistry));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> PhysicsCatalogInitializer.validateSolverBinding(
                FirstLawThermodynamicsModule.NUMERICAL_SOLVER_ID, FirstLawThermodynamicsModule.REFERENCE_SOLVER_ID,
                typedModules, emptyNumericalRegistry, emptyReferenceRegistry));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> PhysicsCatalogInitializer.validateSolverBinding(
                RadioactiveDecayModule.NUMERICAL_SOLVER_ID, RadioactiveDecayModule.REFERENCE_SOLVER_ID,
                typedModules, emptyNumericalRegistry, emptyReferenceRegistry));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> PhysicsCatalogInitializer.validateSolverBinding(
                StandingWaveModule.NUMERICAL_SOLVER_ID, StandingWaveModule.REFERENCE_SOLVER_ID,
                typedModules, emptyNumericalRegistry, emptyReferenceRegistry));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> PhysicsCatalogInitializer.validateSolverBinding(
                CalorimetryMixingModule.NUMERICAL_SOLVER_ID, CalorimetryMixingModule.REFERENCE_SOLVER_ID,
                typedModules, emptyNumericalRegistry, emptyReferenceRegistry));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> PhysicsCatalogInitializer.validateSolverBinding(
                MeasurementUncertaintyModule.NUMERICAL_SOLVER_ID, MeasurementUncertaintyModule.REFERENCE_SOLVER_ID,
                typedModules, emptyNumericalRegistry, emptyReferenceRegistry));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> PhysicsCatalogInitializer.validateSolverBinding(
                DiffractionPolarizationModule.NUMERICAL_SOLVER_ID,
                DiffractionPolarizationModule.REFERENCE_SOLVER_ID,
                typedModules, emptyNumericalRegistry, emptyReferenceRegistry));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> PhysicsCatalogInitializer.validateSolverBinding(
                MomentEquilibriumModule.NUMERICAL_SOLVER_ID, MomentEquilibriumModule.REFERENCE_SOLVER_ID,
                typedModules, emptyNumericalRegistry, emptyReferenceRegistry));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> PhysicsCatalogInitializer.validateSolverBinding(
                NuclearEnergyModule.NUMERICAL_SOLVER_ID, NuclearEnergyModule.REFERENCE_SOLVER_ID,
                typedModules, emptyNumericalRegistry, emptyReferenceRegistry));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> PhysicsCatalogInitializer.validateSolverBinding(
                PhaseChangeModule.NUMERICAL_SOLVER_ID, PhaseChangeModule.REFERENCE_SOLVER_ID,
                typedModules, emptyNumericalRegistry, emptyReferenceRegistry));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> PhysicsCatalogInitializer.validateSolverBinding(
                LightInterferenceModule.NUMERICAL_SOLVER_ID, LightInterferenceModule.REFERENCE_SOLVER_ID,
                typedModules, emptyNumericalRegistry, emptyReferenceRegistry));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> PhysicsCatalogInitializer.validateSolverBinding(
                WavePulseModule.NUMERICAL_SOLVER_ID, WavePulseModule.REFERENCE_SOLVER_ID,
                typedModules, emptyNumericalRegistry, emptyReferenceRegistry));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> PhysicsCatalogInitializer.validateSolverBinding(
                WaveSuperpositionModule.NUMERICAL_SOLVER_ID, WaveSuperpositionModule.REFERENCE_SOLVER_ID,
                typedModules, emptyNumericalRegistry, emptyReferenceRegistry));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> PhysicsCatalogInitializer.validateSolverBinding(
                WaveReflectionModule.NUMERICAL_SOLVER_ID, WaveReflectionModule.REFERENCE_SOLVER_ID,
                typedModules, emptyNumericalRegistry, emptyReferenceRegistry));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> PhysicsCatalogInitializer.validateSolverBinding(
                KinematicsProjectileModule.NUMERICAL_SOLVER_ID, KinematicsProjectileModule.REFERENCE_SOLVER_ID,
                typedModules, emptyNumericalRegistry, emptyReferenceRegistry));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> PhysicsCatalogInitializer.validateSolverBinding(
                StringWaveModule.NUMERICAL_SOLVER_ID, StringWaveModule.REFERENCE_SOLVER_ID,
                typedModules, emptyNumericalRegistry, emptyReferenceRegistry));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> PhysicsCatalogInitializer.validateSolverBinding(
                MagneticForceModule.NUMERICAL_SOLVER_ID, MagneticForceModule.REFERENCE_SOLVER_ID,
                typedModules, emptyNumericalRegistry, emptyReferenceRegistry));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> PhysicsCatalogInitializer.validateSolverBinding(
                RadiationSafetyModule.NUMERICAL_SOLVER_ID, RadiationSafetyModule.REFERENCE_SOLVER_ID,
                typedModules, emptyNumericalRegistry, emptyReferenceRegistry));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> PhysicsCatalogInitializer.validateSolverBinding(
                PointChargeFieldModule.NUMERICAL_SOLVER_ID, PointChargeFieldModule.REFERENCE_SOLVER_ID,
                typedModules, emptyNumericalRegistry, emptyReferenceRegistry));
        org.junit.jupiter.api.Assertions.assertDoesNotThrow(() -> PhysicsCatalogInitializer.validateSolverBinding(
                WaterSurfaceInterferenceModule.NUMERICAL_SOLVER_ID,
                WaterSurfaceInterferenceModule.REFERENCE_SOLVER_ID,
                typedModules, emptyNumericalRegistry, emptyReferenceRegistry));
    }

    @Test
    void typedModelVersionsKeepPreviousPublishedBindingsPinned() throws Exception {
        JsonNode catalog;
        try (InputStream input = new ClassPathResource("schemas/catalog.json").getInputStream()) {
            catalog = objectMapper.readTree(input);
        }

        assertVersionBinding(catalog, "circular_motion", "1.0", "mechanics_foundation_solver",
                "mechanics_foundation_reference");
        assertVersionBinding(catalog, "circular_motion", "1.1", CircularMotionModule.NUMERICAL_SOLVER_ID,
                CircularMotionModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "adiabatic_gas", "1.1", "adiabatic_gas_solver", "adiabatic_gas_reference");
        assertVersionBinding(catalog, "adiabatic_gas", "1.2", AdiabaticGasModule.NUMERICAL_SOLVER_ID,
                AdiabaticGasModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "thermal_expansion", "1.1", "thermal_expansion_solver",
                "thermal_expansion_reference");
        assertVersionBinding(catalog, "thermal_expansion", "1.2", ThermalExpansionModule.NUMERICAL_SOLVER_ID,
                ThermalExpansionModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "work_energy_power", "1.0", "mechanics_foundation_solver",
                "mechanics_foundation_reference");
        assertVersionBinding(catalog, "work_energy_power", "1.1", WorkEnergyPowerModule.NUMERICAL_SOLVER_ID,
                WorkEnergyPowerModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "ideal_gas_isochoric", "1.0", "gas_process_solver",
                "gas_process_reference");
        assertVersionBinding(catalog, "ideal_gas_isochoric", "1.1",
                IdealGasIsochoricModule.NUMERICAL_SOLVER_ID, IdealGasIsochoricModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "circuits_rc_charging", "1.9", "circuit_solver", "circuit_reference");
        assertVersionBinding(catalog, "circuits_rc_charging", "1.10", RcChargingModule.NUMERICAL_SOLVER_ID,
                RcChargingModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "circuits_rc_discharging", "1.9", "circuit_solver", "circuit_reference");
        assertVersionBinding(catalog, "circuits_rc_discharging", "1.10",
                RcDischargingModule.NUMERICAL_SOLVER_ID, RcDischargingModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "ideal_gas_isothermal", "1.0", "ideal_gas_solver", "ideal_gas_reference");
        assertVersionBinding(catalog, "ideal_gas_isothermal", "1.1",
                IdealGasIsothermalModule.NUMERICAL_SOLVER_ID, IdealGasIsothermalModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "linear_drag_motion", "1.0", "linear_drag_solver", "linear_drag_reference");
        assertVersionBinding(catalog, "linear_drag_motion", "1.1",
                LinearDragMotionModule.NUMERICAL_SOLVER_ID, LinearDragMotionModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "ideal_gas_isobaric", "1.0", "gas_process_solver", "gas_process_reference");
        assertVersionBinding(catalog, "ideal_gas_isobaric", "1.1",
                IdealGasIsobaricModule.NUMERICAL_SOLVER_ID, IdealGasIsobaricModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "uniform_electric_field", "1.0",
                "uniform_electric_field_solver", "uniform_electric_field_reference");
        assertVersionBinding(catalog, "uniform_electric_field", "1.1",
                UniformElectricFieldModule.NUMERICAL_SOLVER_ID, UniformElectricFieldModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "kinematics", "1.9", "kinematics_solver", "kinematics_reference");
        assertVersionBinding(catalog, "kinematics", "1.10",
                UniformAccelerationModule.NUMERICAL_SOLVER_ID, UniformAccelerationModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "temperature_scales", "1.1",
                "temperature_scale_solver", "temperature_scale_reference");
        assertVersionBinding(catalog, "temperature_scales", "1.2",
                TemperatureScalesModule.NUMERICAL_SOLVER_ID, TemperatureScalesModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "photoelectric_effect", "1.1",
                "photoelectric_solver", "photoelectric_reference");
        assertVersionBinding(catalog, "photoelectric_effect", "1.2",
                PhotoelectricEffectModule.NUMERICAL_SOLVER_ID, PhotoelectricEffectModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "sound_wave", "1.0", "sound_wave_solver", "sound_wave_reference");
        assertVersionBinding(catalog, "sound_wave", "1.1",
                SoundWaveModule.NUMERICAL_SOLVER_ID, SoundWaveModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "first_law_thermodynamics", "1.1",
                "first_law_solver", "first_law_reference");
        assertVersionBinding(catalog, "first_law_thermodynamics", "1.2",
                FirstLawThermodynamicsModule.NUMERICAL_SOLVER_ID,
                FirstLawThermodynamicsModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "radioactive_decay", "1.0",
                "radioactive_decay_solver", "radioactive_decay_reference");
        assertVersionBinding(catalog, "radioactive_decay", "1.1",
                RadioactiveDecayModule.NUMERICAL_SOLVER_ID, RadioactiveDecayModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "standing_wave", "1.1",
                "standing_wave_solver", "standing_wave_reference");
        assertVersionBinding(catalog, "standing_wave", "1.2",
                StandingWaveModule.NUMERICAL_SOLVER_ID, StandingWaveModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "calorimetry_mixing", "1.1",
                "calorimetry_solver", "calorimetry_reference");
        assertVersionBinding(catalog, "calorimetry_mixing", "1.2",
                CalorimetryMixingModule.NUMERICAL_SOLVER_ID, CalorimetryMixingModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "measurement_uncertainty", "1.1",
                "measurement_uncertainty_solver", "measurement_uncertainty_reference");
        assertVersionBinding(catalog, "measurement_uncertainty", "1.2",
                MeasurementUncertaintyModule.NUMERICAL_SOLVER_ID,
                MeasurementUncertaintyModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "diffraction_polarization", "1.1",
                "diffraction_solver", "diffraction_reference");
        assertVersionBinding(catalog, "diffraction_polarization", "1.2",
                DiffractionPolarizationModule.NUMERICAL_SOLVER_ID,
                DiffractionPolarizationModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "moment_equilibrium", "1.0",
                "moment_equilibrium_solver", "moment_equilibrium_reference");
        assertVersionBinding(catalog, "moment_equilibrium", "1.1",
                MomentEquilibriumModule.NUMERICAL_SOLVER_ID, MomentEquilibriumModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "nuclear_energy", "1.1",
                "nuclear_energy_solver", "nuclear_energy_reference");
        assertVersionBinding(catalog, "nuclear_energy", "1.2",
                NuclearEnergyModule.NUMERICAL_SOLVER_ID, NuclearEnergyModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "phase_change", "1.0",
                "phase_change_solver", "phase_change_reference");
        assertVersionBinding(catalog, "phase_change", "1.1",
                PhaseChangeModule.NUMERICAL_SOLVER_ID, PhaseChangeModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "light_interference", "1.1",
                "light_interference_solver", "light_interference_reference");
        assertVersionBinding(catalog, "light_interference", "1.2",
                LightInterferenceModule.NUMERICAL_SOLVER_ID, LightInterferenceModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "wave_pulse", "1.1", "wave_pulse_solver", "wave_pulse_reference");
        assertVersionBinding(catalog, "wave_pulse", "1.2",
                WavePulseModule.NUMERICAL_SOLVER_ID, WavePulseModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "wave_superposition", "1.0",
                "wave_superposition_solver", "wave_superposition_reference");
        assertVersionBinding(catalog, "wave_superposition", "1.1",
                WaveSuperpositionModule.NUMERICAL_SOLVER_ID, WaveSuperpositionModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "wave_reflection", "1.0",
                "wave_reflection_solver", "wave_reflection_reference");
        assertVersionBinding(catalog, "wave_reflection", "1.1",
                WaveReflectionModule.NUMERICAL_SOLVER_ID, WaveReflectionModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "kinematics_projectile", "1.9",
                "kinematics_solver", "kinematics_reference");
        assertVersionBinding(catalog, "kinematics_projectile", "1.10",
                KinematicsProjectileModule.NUMERICAL_SOLVER_ID, KinematicsProjectileModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "string_wave", "1.1", "string_wave_solver", "string_wave_reference");
        assertVersionBinding(catalog, "string_wave", "1.2",
                StringWaveModule.NUMERICAL_SOLVER_ID, StringWaveModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "magnetic_force", "1.1", "magnetic_force_solver", "magnetic_force_reference");
        assertVersionBinding(catalog, "magnetic_force", "1.2",
                MagneticForceModule.NUMERICAL_SOLVER_ID, MagneticForceModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "radiation_safety", "1.0",
                "radiation_safety_solver", "radiation_safety_reference");
        assertVersionBinding(catalog, "radiation_safety", "1.1",
                RadiationSafetyModule.NUMERICAL_SOLVER_ID, RadiationSafetyModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "point_charge_field", "1.0",
                "electric_field_solver", "electric_field_reference");
        assertVersionBinding(catalog, "point_charge_field", "1.1",
                PointChargeFieldModule.NUMERICAL_SOLVER_ID, PointChargeFieldModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "water_surface_interference", "1.0",
                "water_surface_interference_solver", "water_surface_interference_reference");
        assertVersionBinding(catalog, "water_surface_interference", "1.1",
                WaterSurfaceInterferenceModule.NUMERICAL_SOLVER_ID,
                WaterSurfaceInterferenceModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "thermistor_response", "1.1",
                ThermistorResponseModule.NUMERICAL_SOLVER_ID, ThermistorResponseModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "sensor_op_amp", "1.1",
                SensorOpAmpModule.NUMERICAL_SOLVER_ID, SensorOpAmpModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "ac_rlc_circuit", "1.2",
                AcRlcCircuitModule.NUMERICAL_SOLVER_ID, AcRlcCircuitModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "electromagnetic_induction", "1.2",
                ElectromagneticInductionModule.NUMERICAL_SOLVER_ID,
                ElectromagneticInductionModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "simple_magnifier", "1.2",
                SimpleMagnifierModule.NUMERICAL_SOLVER_ID, SimpleMagnifierModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "compound_microscope", "1.2",
                CompoundMicroscopeModule.NUMERICAL_SOLVER_ID, CompoundMicroscopeModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "astronomical_telescope", "1.2",
                AstronomicalTelescopeModule.NUMERICAL_SOLVER_ID,
                AstronomicalTelescopeModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "xray_imaging", "1.1",
                XrayImagingModule.NUMERICAL_SOLVER_ID, XrayImagingModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "ct_reconstruction", "1.1",
                CtReconstructionModule.NUMERICAL_SOLVER_ID, CtReconstructionModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "mri_relaxation", "1.1",
                MriRelaxationModule.NUMERICAL_SOLVER_ID, MriRelaxationModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "de_broglie_diffraction", "1.2",
                DeBroglieDiffractionModule.NUMERICAL_SOLVER_ID,
                DeBroglieDiffractionModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "eclipse_geometry", "1.1",
                EclipseGeometryModule.NUMERICAL_SOLVER_ID, EclipseGeometryModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "energy_band_transition", "1.1",
                EnergyBandTransitionModule.NUMERICAL_SOLVER_ID,
                EnergyBandTransitionModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "nuclear_reaction_energy", "1.1",
                NuclearReactionEnergyModule.NUMERICAL_SOLVER_ID,
                NuclearReactionEnergyModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "atomic_spectra", "1.2",
                AtomicSpectraModule.NUMERICAL_SOLVER_ID, AtomicSpectraModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "dynamics_forces", "2.0",
                DynamicsForcesModule.NUMERICAL_SOLVER_ID, DynamicsForcesModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "dynamics_collision", "2.0",
                DynamicsCollisionModule.NUMERICAL_SOLVER_ID, DynamicsCollisionModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "oscillations_spring", "2.0",
                SpringOscillationModule.NUMERICAL_SOLVER_ID, SpringOscillationModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "kinematics_position_time_graph", "1.1",
                PositionTimeGraphModule.NUMERICAL_SOLVER_ID, PositionTimeGraphModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "kinematics_velocity_time_graph", "1.1",
                VelocityTimeGraphModule.NUMERICAL_SOLVER_ID, VelocityTimeGraphModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "kinematics_acceleration_time_graph", "1.1",
                AccelerationTimeGraphModule.NUMERICAL_SOLVER_ID,
                AccelerationTimeGraphModule.REFERENCE_SOLVER_ID);
    }

    @Test
    void versionedPhysicsDefaultsMaterializeBeforeTypedBinding() throws Exception {
        JsonNode catalog;
        try (InputStream input = new ClassPathResource("schemas/catalog.json").getInputStream()) {
            catalog = objectMapper.readTree(input);
        }
        SchemaCompiler schemaCompiler = new SchemaCompiler(objectMapper);
        CanonicalQuantityCompiler quantityCompiler = new CanonicalQuantityCompiler(new UnitNormalizer(objectMapper));

        JsonNode photoelectricEntry = findVersion(catalog, "photoelectric_effect", "1.2");
        com.fasterxml.jackson.databind.node.ObjectNode photoelectricDefinition =
                (com.fasterxml.jackson.databind.node.ObjectNode) photoelectricEntry.path("definition").deepCopy();
        photoelectricDefinition.put("model", photoelectricEntry.path("model").asText());
        var photoelectricSchema = schemaCompiler.compile(photoelectricDefinition, "photoelectric_effect");
        JsonNode photoelectricInput = objectMapper.readTree("""
                {"quantities":[
                  {"name":"photon_frequency","value":5.0e14,"originalUnit":"Hz"},
                  {"name":"work_function","value":2.0e-19,"originalUnit":"J"}
                ]}
                """);
        var photoelectricBag = quantityCompiler.compile(photoelectricSchema, photoelectricInput, java.util.Map.of());
        assertEquals(1.602176634e-19, photoelectricBag.require("electron_charge"), 1.0e-33);
        assertEquals("C", photoelectricBag.unit("electron_charge"));
        var photoelectricParameters = new PhotoelectricEffectModule().bind(photoelectricBag);
        assertEquals(5.0e14, photoelectricParameters.photonFrequency());
        assertEquals(2.0e-19, photoelectricParameters.workFunction(), 1.0e-33);

        JsonNode soundWaveEntry = findVersion(catalog, "sound_wave", "1.1");
        com.fasterxml.jackson.databind.node.ObjectNode soundWaveDefinition =
                (com.fasterxml.jackson.databind.node.ObjectNode) soundWaveEntry.path("definition").deepCopy();
        soundWaveDefinition.put("model", soundWaveEntry.path("model").asText());
        var soundWaveSchema = schemaCompiler.compile(soundWaveDefinition, "sound_wave");
        JsonNode soundWaveInput = objectMapper.readTree("""
                {"quantities":[
                  {"name":"pressure_amplitude","value":2,"originalUnit":"Pa"},
                  {"name":"frequency","value":2,"originalUnit":"Hz"},
                  {"name":"sound_speed","value":4,"originalUnit":"m/s"},
                  {"name":"probe_position","value":0,"originalUnit":"m"}
                ]}
                """);
        var soundWaveBag = quantityCompiler.compile(soundWaveSchema, soundWaveInput, java.util.Map.of());
        var soundWaveParameters = new SoundWaveModule().bind(soundWaveBag);
        assertEquals(0.0, soundWaveParameters.phase());
        assertEquals(0.0, soundWaveParameters.domainStart());
        assertEquals(1.0, soundWaveParameters.domainEnd());
        assertEquals(2001, soundWaveParameters.spatialSamples());
        assertEquals(0.0, soundWaveParameters.probePosition());

        JsonNode standingWaveEntry = findVersion(catalog, "standing_wave", "1.2");
        com.fasterxml.jackson.databind.node.ObjectNode standingWaveDefinition =
                (com.fasterxml.jackson.databind.node.ObjectNode) standingWaveEntry.path("definition").deepCopy();
        standingWaveDefinition.put("model", standingWaveEntry.path("model").asText());
        var standingWaveSchema = schemaCompiler.compile(standingWaveDefinition, "standing_wave");
        JsonNode standingWaveInput = objectMapper.readTree("""
                {"quantities":[
                  {"name":"amplitude","value":0.2,"originalUnit":"m"},
                  {"name":"frequency","value":1,"originalUnit":"Hz"},
                  {"name":"wave_speed","value":2,"originalUnit":"m/s"},
                  {"name":"string_length","value":1,"originalUnit":"m"},
                  {"name":"probe_position","value":0.5,"originalUnit":"m"}
                ]}
                """);
        var standingWaveBag = quantityCompiler.compile(standingWaveSchema, standingWaveInput, java.util.Map.of());
        var standingWaveParameters = new StandingWaveModule().bind(standingWaveBag);
        assertEquals(0.0, standingWaveParameters.phase());
        assertEquals(0.0, standingWaveParameters.domainStart());
        assertEquals(801, standingWaveParameters.spatialSamples());
        assertEquals(0.5, standingWaveParameters.probePosition());
        org.junit.jupiter.api.Assertions.assertThrows(IllegalArgumentException.class,
                () -> new StandingWaveModule().bind(quantityCompiler.compile(standingWaveSchema, objectMapper.readTree("""
                        {"quantities":[
                          {"name":"amplitude","value":0.2,"originalUnit":"m"},
                          {"name":"frequency","value":1,"originalUnit":"Hz"},
                          {"name":"wave_speed","value":2,"originalUnit":"m/s"},
                          {"name":"string_length","value":1,"originalUnit":"m"}
                        ]}
                        """), java.util.Map.of())));

        JsonNode nuclearEntry = findVersion(catalog, "nuclear_energy", "1.2");
        com.fasterxml.jackson.databind.node.ObjectNode nuclearDefinition =
                (com.fasterxml.jackson.databind.node.ObjectNode) nuclearEntry.path("definition").deepCopy();
        nuclearDefinition.put("model", nuclearEntry.path("model").asText());
        var nuclearSchema = schemaCompiler.compile(nuclearDefinition, "nuclear_energy");
        var nuclearBag = quantityCompiler.compile(nuclearSchema, objectMapper.readTree("""
                {"quantities":[{"name":"mass_defect","value":1.0e-30,"originalUnit":"kg"}]}
                """), java.util.Map.of());
        assertEquals(1.0, nuclearBag.require("reaction_count"));
        assertEquals("1", nuclearBag.unit("reaction_count"));
        assertEquals(1.0, new NuclearEnergyModule().bind(nuclearBag).reactionCount());

        JsonNode lightInterferenceEntry = findVersion(catalog, "light_interference", "1.2");
        com.fasterxml.jackson.databind.node.ObjectNode lightInterferenceDefinition =
                (com.fasterxml.jackson.databind.node.ObjectNode) lightInterferenceEntry.path("definition").deepCopy();
        lightInterferenceDefinition.put("model", lightInterferenceEntry.path("model").asText());
        var lightInterferenceSchema = schemaCompiler.compile(lightInterferenceDefinition, "light_interference");
        var lightInterferenceBag = quantityCompiler.compile(lightInterferenceSchema, objectMapper.readTree("""
                {"quantities":[
                  {"name":"wavelength","value":5.0e-7,"originalUnit":"m"},
                  {"name":"path_difference","value":1.25e-7,"originalUnit":"m"},
                  {"name":"reference_intensity","value":12,"originalUnit":"W/m2"}
                ]}
                """), java.util.Map.of());
        assertEquals(0.0, lightInterferenceBag.require("path_difference_rate"));
        assertEquals("m/s", lightInterferenceBag.unit("path_difference_rate"));
        assertEquals(0.0, new LightInterferenceModule().bind(lightInterferenceBag).pathDifferenceRate());

        JsonNode superpositionEntry = findVersion(catalog, "wave_superposition", "1.1");
        com.fasterxml.jackson.databind.node.ObjectNode superpositionDefinition =
                (com.fasterxml.jackson.databind.node.ObjectNode) superpositionEntry.path("definition").deepCopy();
        superpositionDefinition.put("model", superpositionEntry.path("model").asText());
        var superpositionSchema = schemaCompiler.compile(superpositionDefinition, "wave_superposition");
        var superpositionBag = quantityCompiler.compile(superpositionSchema, objectMapper.readTree("""
                {"quantities":[
                  {"name":"amplitude_1","value":0.3,"originalUnit":"m"},
                  {"name":"amplitude_2","value":0.4,"originalUnit":"m"},
                  {"name":"frequency","value":1,"originalUnit":"Hz"},
                  {"name":"wave_speed","value":2,"originalUnit":"m/s"}
                ]}
                """), java.util.Map.of());
        var superpositionParameters = new WaveSuperpositionModule().bind(superpositionBag);
        assertEquals(0.0, superpositionParameters.phase1());
        assertEquals(0.0, superpositionParameters.phase2());
        assertEquals(-5.0, superpositionParameters.domainStart());
        assertEquals(5.0, superpositionParameters.domainEnd());
        assertEquals(801, superpositionParameters.spatialSamples());
        assertEquals(0.0, superpositionParameters.probePosition());

        JsonNode reflectionEntry = findVersion(catalog, "wave_reflection", "1.1");
        com.fasterxml.jackson.databind.node.ObjectNode reflectionDefinition =
                (com.fasterxml.jackson.databind.node.ObjectNode) reflectionEntry.path("definition").deepCopy();
        reflectionDefinition.put("model", reflectionEntry.path("model").asText());
        var reflectionSchema = schemaCompiler.compile(reflectionDefinition, "wave_reflection");
        var reflectionBag = quantityCompiler.compile(reflectionSchema, objectMapper.readTree("""
                {"quantities":[
                  {"name":"amplitude","value":0.4,"originalUnit":"m"},
                  {"name":"wave_speed","value":2,"originalUnit":"m/s"},
                  {"name":"pulse_width","value":0.5,"originalUnit":"m"},
                  {"name":"initial_position","value":1,"originalUnit":"m"},
                  {"name":"boundary_position","value":4,"originalUnit":"m"}
                ]}
                """), java.util.Map.of());
        assertEquals(-1.0, reflectionBag.require("reflection_coefficient"));
        assertEquals("1", reflectionBag.unit("reflection_coefficient"));
        var reflectionParameters = new WaveReflectionModule().bind(reflectionBag);
        assertEquals(0.0, reflectionParameters.domainStart());
        assertEquals(801, reflectionParameters.spatialSamples());
        assertEquals(0.0, reflectionParameters.probePosition());
        assertEquals(-1.0, reflectionParameters.reflectionCoefficient());

        JsonNode projectileEntry = findVersion(catalog, "kinematics_projectile", "1.10");
        com.fasterxml.jackson.databind.node.ObjectNode projectileDefinition =
                (com.fasterxml.jackson.databind.node.ObjectNode) projectileEntry.path("definition").deepCopy();
        projectileDefinition.put("model", projectileEntry.path("model").asText());
        var projectileSchema = schemaCompiler.compile(projectileDefinition, "kinematics_projectile");
        var projectileBag = quantityCompiler.compile(projectileSchema, objectMapper.readTree("""
                {"quantities":[
                  {"name":"initial_position","value":3,"originalUnit":"m"},
                  {"name":"initial_height","value":5,"originalUnit":"m"},
                  {"name":"initial_velocity","value":10,"originalUnit":"m/s"},
                  {"name":"launch_angle","value":0.7853981633974483,"originalUnit":"rad"}
                ]}
                """), java.util.Map.of());
        assertEquals(9.81, projectileBag.require("gravitational_acceleration"), 1.0e-12);
        assertEquals("m/s2", projectileBag.unit("gravitational_acceleration"));
        assertEquals(9.81, new KinematicsProjectileModule().bind(projectileBag).gravity(), 1.0e-12);

        JsonNode stringWaveEntry = findVersion(catalog, "string_wave", "1.2");
        com.fasterxml.jackson.databind.node.ObjectNode stringWaveDefinition =
                (com.fasterxml.jackson.databind.node.ObjectNode) stringWaveEntry.path("definition").deepCopy();
        stringWaveDefinition.put("model", stringWaveEntry.path("model").asText());
        var stringWaveSchema = schemaCompiler.compile(stringWaveDefinition, "string_wave");
        var stringWaveBag = quantityCompiler.compile(stringWaveSchema, objectMapper.readTree("""
                {"quantities":[
                  {"name":"amplitude","value":0.02,"originalUnit":"m"},
                  {"name":"frequency","value":2,"originalUnit":"Hz"},
                  {"name":"wave_speed","value":4,"originalUnit":"m/s"},
                  {"name":"domain_length","value":4,"originalUnit":"m"}
                ]}
                """), java.util.Map.of());
        var stringWaveParameters = new StringWaveModule().bind(stringWaveBag);
        assertEquals(0.0, stringWaveParameters.phase());
        assertEquals(0.0, stringWaveParameters.probePosition());
        assertEquals(801, stringWaveParameters.spatialSamples());
    }

    @Test
    void waterSurfaceV11CompilesTypedFieldAndProbeContractsWhileV10BindingStaysHistorical() throws Exception {
        JsonNode catalog;
        try (InputStream input = new ClassPathResource("schemas/catalog.json").getInputStream()) {
            catalog = objectMapper.readTree(input);
        }

        JsonNode legacy = findVersion(catalog, "water_surface_interference", "1.0");
        assertEquals("water_surface_interference_solver", legacy.path("solverId").asText());
        assertEquals("water_surface_interference_reference", legacy.path("referenceSolverId").asText());

        JsonNode entry = findVersion(catalog, "water_surface_interference", "1.1");
        var definition = (com.fasterxml.jackson.databind.node.ObjectNode) entry.path("definition").deepCopy();
        definition.put("model", entry.path("model").asText());
        definition.put("version", entry.path("version").asText());
        var compiled = new SchemaCompiler(objectMapper).compile(definition, "water_surface_interference");
        assertEquals(java.util.Set.of("centerHeight", "waterSurface"), compiled.outputDefinitions().keySet());
        assertEquals("TIME_SERIES", compiled.outputDefinitions().get("centerHeight").kind().name());
        assertEquals("SCALAR_FIELD", compiled.outputDefinitions().get("waterSurface").kind().name());

        var quantities = new CanonicalQuantityCompiler(new UnitNormalizer(objectMapper)).compile(compiled,
                objectMapper.readTree("""
                        {"quantities":[
                          {"name":"wavelength","value":2,"originalUnit":"m"},
                          {"name":"wave_speed","value":1,"originalUnit":"m/s"},
                          {"name":"source_separation","value":0.5,"originalUnit":"m"},
                          {"name":"amplitude","value":0.2,"originalUnit":"m"},
                          {"name":"domain_size","value":2,"originalUnit":"m"},
                          {"name":"spatial_samples","value":9,"originalUnit":"1"}
                        ]}
                        """), java.util.Map.of());
        var parameters = new WaterSurfaceInterferenceModule().bind(quantities);
        assertEquals(9, parameters.spatialSamples());
    }

    @Test
    void finalTypedModelVersionsBindToDistinctRegisteredPairs() throws Exception {
        JsonNode catalog;
        try (InputStream input = new ClassPathResource("schemas/catalog.json").getInputStream()) {
            catalog = objectMapper.readTree(input);
        }

        assertVersionBinding(catalog, "radio_signal_chain", "1.1",
                RadioSignalChainModule.NUMERICAL_SOLVER_ID, RadioSignalChainModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "radio_communication", "1.1",
                RadioCommunicationModule.NUMERICAL_SOLVER_ID, RadioCommunicationModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "ultrasound_imaging", "1.1",
                UltrasoundImagingModule.NUMERICAL_SOLVER_ID, UltrasoundImagingModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "energy_environment", "1.1",
                EnergyEnvironmentModule.NUMERICAL_SOLVER_ID, EnergyEnvironmentModule.REFERENCE_SOLVER_ID);
        assertVersionBinding(catalog, "experimental_data_graph", "1.1",
                ExperimentalDataGraphModule.NUMERICAL_SOLVER_ID, ExperimentalDataGraphModule.REFERENCE_SOLVER_ID);
    }

    private JsonNode findVersion(JsonNode catalog, String schemaId, String version) {
        for (JsonNode entry : catalog) {
            if (schemaId.equals(entry.path("schemaId").asText())
                    && version.equals(entry.path("version").asText())) return entry;
        }
        throw new AssertionError("Missing catalog entry " + schemaId + "@" + version);
    }

    private static void assertVersionBinding(JsonNode catalog, String schemaId, String version,
                                            String numericalSolverId, String referenceSolverId) {
        JsonNode entry = null;
        for (JsonNode candidate : catalog) {
            if (schemaId.equals(candidate.path("schemaId").asText())
                    && version.equals(candidate.path("version").asText())) {
                entry = candidate;
                break;
            }
        }
        org.junit.jupiter.api.Assertions.assertNotNull(entry, schemaId + "@" + version);
        assertEquals(numericalSolverId, entry.path("solverId").asText());
        assertEquals(referenceSolverId, entry.path("referenceSolverId").asText());
    }

    @Test
    void diodeVersionUpgradePreservesTheOldBindingAndAddsTheTypedPair() throws Exception {
        JsonNode catalog;
        try (InputStream input = new ClassPathResource("schemas/catalog.json").getInputStream()) {
            catalog = objectMapper.readTree(input);
        }

        JsonNode legacy = null;
        JsonNode typed = null;
        for (JsonNode entry : catalog) {
            if (!"diode_characteristic".equals(entry.path("schemaId").asText())) continue;
            if ("1.0".equals(entry.path("version").asText())) legacy = entry;
            if ("1.1".equals(entry.path("version").asText())) typed = entry;
        }

        org.junit.jupiter.api.Assertions.assertNotNull(legacy);
        assertEquals("applications_solver", legacy.path("solverId").asText());
        assertEquals("applications_reference", legacy.path("referenceSolverId").asText());
        org.junit.jupiter.api.Assertions.assertNotNull(typed);
        assertEquals(DiodeCharacteristicModule.NUMERICAL_SOLVER_ID, typed.path("solverId").asText());
        assertEquals(DiodeCharacteristicModule.REFERENCE_SOLVER_ID, typed.path("referenceSolverId").asText());
        assertEquals("diode_characteristic", typed.path("definition").path("visualization").path("scene").asText());
    }

    @Test
    void canonicalObjectKeyOrderDoesNotBlockSafeChecksumBackfill() throws Exception {
        JsonNode stored = objectMapper.readTree("{\"nested\":{\"b\":2,\"a\":1},\"items\":[{\"y\":true,\"x\":false}]}");
        JsonNode source = objectMapper.readTree("{\"items\":[{\"x\":false,\"y\":true}],\"nested\":{\"a\":1,\"b\":2}}");

        assertTrue(SchemaCatalogIntegrity.definitionsMatch(stored, source));
        assertDoesNotThrow(() -> SchemaCatalogIntegrity.requireDefinitionsMatch("orbit", "1.0", stored, source));

        SchemaCompiler compiler = new SchemaCompiler(objectMapper);
        assertTrue(compiler.checksum(source).equals(SchemaCatalogIntegrity.checksumForVerifiedStoredDefinition(
                "orbit", "1.0", stored, source, compiler::checksum)));
        assertFalse(compiler.checksum(stored).equals(compiler.checksum(source)));
    }

    @Test
    void changedArrayOrderOrValueCannotBeBackfilled() throws Exception {
        JsonNode stored = objectMapper.readTree("{\"values\":[1,2],\"label\":\"old\"}");
        JsonNode changedOrder = objectMapper.readTree("{\"label\":\"old\",\"values\":[2,1]}");
        JsonNode changedValue = objectMapper.readTree("{\"label\":\"new\",\"values\":[1,2]}");
        JsonNode integerValue = objectMapper.readTree("{\"value\":1}");
        JsonNode decimalValue = objectMapper.readTree("{\"value\":1.0}");

        assertFalse(SchemaCatalogIntegrity.definitionsMatch(stored, changedOrder));
        assertFalse(SchemaCatalogIntegrity.definitionsMatch(stored, changedValue));
        assertFalse(SchemaCatalogIntegrity.definitionsMatch(integerValue, decimalValue));
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> SchemaCatalogIntegrity.requireDefinitionsMatch("orbit", "2.1", stored, changedValue));
        assertTrue(failure.getMessage().contains("orbit@2.1"));
        assertTrue(failure.getMessage().contains("create a new schema version"));
    }

    @Test
    void solverBindingChecksumRequiresStoredBindingToMatchSource() throws Exception {
        SchemaCompiler compiler = new SchemaCompiler(objectMapper);
        JsonNode stored = objectMapper.readTree("{\"referenceSolverId\":\"orbit_ref\",\"output\":{\"type\":\"series\"}}");
        JsonNode source = objectMapper.readTree("{\"output\":{\"type\":\"series\"},\"referenceSolverId\":\"orbit_ref\"}");
        String verified = SchemaCatalogIntegrity.checksumForVerifiedSolverBinding(
                "orbit", "1.0", "orbit_solver", stored, "orbit_solver", source, compiler::checksum);
        assertEquals(compiler.checksum(objectMapper.readTree(
                "{\"solverId\":\"orbit_solver\",\"outputDefinition\":{\"output\":{\"type\":\"series\"},\"referenceSolverId\":\"orbit_ref\"}}")),
                verified);

        JsonNode changed = objectMapper.readTree("{\"output\":{\"type\":\"field\"},\"referenceSolverId\":\"orbit_ref\"}");
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> SchemaCatalogIntegrity.checksumForVerifiedSolverBinding(
                        "orbit", "1.0", "other_solver", stored, "orbit_solver", changed, compiler::checksum));
        assertTrue(failure.getMessage().contains("orbit@1.0"));
        assertTrue(failure.getMessage().contains("create a new schema version"));
    }

    @Test
    void publishedNameAndTopicChangesAreCatalogDrift() {
        assertDoesNotThrow(() -> SchemaCatalogIntegrity.requireMetadataMatches(
                "orbit", "1.0", "Orbit", "KINEMATICS", "Orbit", "KINEMATICS"));
        IllegalStateException failure = assertThrows(IllegalStateException.class,
                () -> SchemaCatalogIntegrity.requireMetadataMatches(
                        "orbit", "1.0", "Orbit", "KINEMATICS", "Orbit motion", "DYNAMICS"));
        assertTrue(failure.getMessage().contains("orbit@1.0"));
        assertTrue(failure.getMessage().contains("create a new schema version"));
    }
}
