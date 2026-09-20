package com.example.backend.physics.module;

import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.exception.SolverBindingException;
import com.example.backend.physics.module.circuits.AcWaveformModule;
import com.example.backend.physics.module.circuits.AcRlcCircuitModule;
import com.example.backend.physics.module.circuits.CapacitorBasicModule;
import com.example.backend.physics.module.circuits.DiodeCharacteristicModule;
import com.example.backend.physics.module.circuits.IdealTransformerModule;
import com.example.backend.physics.module.circuits.OhmsLawModule;
import com.example.backend.physics.module.circuits.ResistorsParallelModule;
import com.example.backend.physics.module.circuits.ResistorsSeriesModule;
import com.example.backend.physics.module.circuits.RcChargingModule;
import com.example.backend.physics.module.circuits.RcDischargingModule;
import com.example.backend.physics.module.circuits.SourceInternalResistanceModule;
import com.example.backend.physics.module.circuits.SensorOpAmpModule;
import com.example.backend.physics.module.circuits.ThermistorResponseModule;
import com.example.backend.physics.module.dynamics.DampedForcedOscillationModule;
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
import com.example.backend.physics.module.dynamics.HookeLawModule;
import com.example.backend.physics.module.dynamics.HydrostaticsModule;
import com.example.backend.physics.module.dynamics.GravityOrbitModule;
import com.example.backend.physics.module.dynamics.WorkEnergyPowerModule;
import com.example.backend.physics.module.kinematics.KinematicsProjectileModule;
import com.example.backend.physics.module.kinematics.AccelerationTimeGraphModule;
import com.example.backend.physics.module.kinematics.PositionTimeGraphModule;
import com.example.backend.physics.module.kinematics.VelocityTimeGraphModule;
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
import com.example.backend.physics.module.optics.SnellRefractionModule;
import com.example.backend.physics.module.optics.ThinLensModule;
import com.example.backend.physics.module.optics.LightInterferenceModule;
import com.example.backend.physics.module.practical.MeasurementUncertaintyModule;
import com.example.backend.physics.module.practical.EnergyEnvironmentModule;
import com.example.backend.physics.module.practical.ExperimentalDataGraphModule;
import com.example.backend.physics.module.optics.DiffractionPolarizationModule;
import com.example.backend.physics.module.optics.AstronomicalTelescopeModule;
import com.example.backend.physics.module.optics.CompoundMicroscopeModule;
import com.example.backend.physics.module.optics.SimpleMagnifierModule;
import com.example.backend.physics.module.thermal.AdiabaticGasModule;
import com.example.backend.physics.module.thermal.IdealGasIsochoricModule;
import com.example.backend.physics.module.thermal.IdealGasIsothermalModule;
import com.example.backend.physics.module.thermal.IdealGasIsobaricModule;
import com.example.backend.physics.module.thermal.ThermalExpansionModule;
import com.example.backend.physics.module.thermal.TemperatureScalesModule;
import com.example.backend.physics.module.thermal.PhaseChangeModule;
import com.example.backend.physics.module.thermal.FirstLawThermodynamicsModule;
import com.example.backend.physics.module.thermal.CalorimetryMixingModule;
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
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PhysicsModuleRegistryTest {
    private final CanonicalQuantityBag quantities = new CanonicalQuantityBag(
            Map.of("peak_voltage", new BigDecimal("10"), "frequency", new BigDecimal("50")),
            Map.of("peak_voltage", "V", "frequency", "Hz"));

    @Test
    void bindsNumericalAndReferenceIdsFromOneCanonicalBag() {
        PhysicsModuleRegistry registry = new PhysicsModuleConfiguration().physicsModuleRegistry();

        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                AcWaveformModule.NUMERICAL_SOLVER_ID, AcWaveformModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                DiodeCharacteristicModule.NUMERICAL_SOLVER_ID, DiodeCharacteristicModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                DampedForcedOscillationModule.NUMERICAL_SOLVER_ID,
                DampedForcedOscillationModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                OhmsLawModule.NUMERICAL_SOLVER_ID, OhmsLawModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                CapacitorBasicModule.NUMERICAL_SOLVER_ID, CapacitorBasicModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                IdealTransformerModule.NUMERICAL_SOLVER_ID, IdealTransformerModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                SourceInternalResistanceModule.NUMERICAL_SOLVER_ID,
                SourceInternalResistanceModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                ResistorsSeriesModule.NUMERICAL_SOLVER_ID, ResistorsSeriesModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                ResistorsParallelModule.NUMERICAL_SOLVER_ID, ResistorsParallelModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                ThinLensModule.NUMERICAL_SOLVER_ID, ThinLensModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                SnellRefractionModule.NUMERICAL_SOLVER_ID, SnellRefractionModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                HookeLawModule.NUMERICAL_SOLVER_ID, HookeLawModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                HydrostaticsModule.NUMERICAL_SOLVER_ID, HydrostaticsModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                GravityOrbitModule.NUMERICAL_SOLVER_ID, GravityOrbitModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                CircularMotionModule.NUMERICAL_SOLVER_ID, CircularMotionModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                AdiabaticGasModule.NUMERICAL_SOLVER_ID, AdiabaticGasModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                ThermalExpansionModule.NUMERICAL_SOLVER_ID, ThermalExpansionModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                WorkEnergyPowerModule.NUMERICAL_SOLVER_ID, WorkEnergyPowerModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                IdealGasIsochoricModule.NUMERICAL_SOLVER_ID, IdealGasIsochoricModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                RcChargingModule.NUMERICAL_SOLVER_ID, RcChargingModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                RcDischargingModule.NUMERICAL_SOLVER_ID, RcDischargingModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                LinearDragMotionModule.NUMERICAL_SOLVER_ID, LinearDragMotionModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                IdealGasIsothermalModule.NUMERICAL_SOLVER_ID, IdealGasIsothermalModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                IdealGasIsobaricModule.NUMERICAL_SOLVER_ID, IdealGasIsobaricModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                UniformElectricFieldModule.NUMERICAL_SOLVER_ID, UniformElectricFieldModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                UniformAccelerationModule.NUMERICAL_SOLVER_ID, UniformAccelerationModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                TemperatureScalesModule.NUMERICAL_SOLVER_ID, TemperatureScalesModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                PhotoelectricEffectModule.NUMERICAL_SOLVER_ID, PhotoelectricEffectModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                SoundWaveModule.NUMERICAL_SOLVER_ID, SoundWaveModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                FirstLawThermodynamicsModule.NUMERICAL_SOLVER_ID, FirstLawThermodynamicsModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                RadioactiveDecayModule.NUMERICAL_SOLVER_ID, RadioactiveDecayModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                StandingWaveModule.NUMERICAL_SOLVER_ID, StandingWaveModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                CalorimetryMixingModule.NUMERICAL_SOLVER_ID, CalorimetryMixingModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                MeasurementUncertaintyModule.NUMERICAL_SOLVER_ID, MeasurementUncertaintyModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                DiffractionPolarizationModule.NUMERICAL_SOLVER_ID,
                DiffractionPolarizationModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                MomentEquilibriumModule.NUMERICAL_SOLVER_ID, MomentEquilibriumModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                NuclearEnergyModule.NUMERICAL_SOLVER_ID, NuclearEnergyModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                PhaseChangeModule.NUMERICAL_SOLVER_ID, PhaseChangeModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                LightInterferenceModule.NUMERICAL_SOLVER_ID, LightInterferenceModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                WavePulseModule.NUMERICAL_SOLVER_ID, WavePulseModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                WaveSuperpositionModule.NUMERICAL_SOLVER_ID, WaveSuperpositionModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                WaveReflectionModule.NUMERICAL_SOLVER_ID, WaveReflectionModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                KinematicsProjectileModule.NUMERICAL_SOLVER_ID, KinematicsProjectileModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                StringWaveModule.NUMERICAL_SOLVER_ID, StringWaveModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                MagneticForceModule.NUMERICAL_SOLVER_ID, MagneticForceModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                RadiationSafetyModule.NUMERICAL_SOLVER_ID, RadiationSafetyModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                PointChargeFieldModule.NUMERICAL_SOLVER_ID, PointChargeFieldModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                WaterSurfaceInterferenceModule.NUMERICAL_SOLVER_ID,
                WaterSurfaceInterferenceModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                AcRlcCircuitModule.NUMERICAL_SOLVER_ID, AcRlcCircuitModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                ThermistorResponseModule.NUMERICAL_SOLVER_ID, ThermistorResponseModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                SensorOpAmpModule.NUMERICAL_SOLVER_ID, SensorOpAmpModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                ElectromagneticInductionModule.NUMERICAL_SOLVER_ID,
                ElectromagneticInductionModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                SimpleMagnifierModule.NUMERICAL_SOLVER_ID, SimpleMagnifierModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                CompoundMicroscopeModule.NUMERICAL_SOLVER_ID, CompoundMicroscopeModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                AstronomicalTelescopeModule.NUMERICAL_SOLVER_ID, AstronomicalTelescopeModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                XrayImagingModule.NUMERICAL_SOLVER_ID, XrayImagingModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                CtReconstructionModule.NUMERICAL_SOLVER_ID, CtReconstructionModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                MriRelaxationModule.NUMERICAL_SOLVER_ID, MriRelaxationModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                DeBroglieDiffractionModule.NUMERICAL_SOLVER_ID, DeBroglieDiffractionModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                EclipseGeometryModule.NUMERICAL_SOLVER_ID, EclipseGeometryModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                EnergyBandTransitionModule.NUMERICAL_SOLVER_ID, EnergyBandTransitionModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                NuclearReactionEnergyModule.NUMERICAL_SOLVER_ID,
                NuclearReactionEnergyModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                AtomicSpectraModule.NUMERICAL_SOLVER_ID, AtomicSpectraModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                DynamicsForcesModule.NUMERICAL_SOLVER_ID, DynamicsForcesModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                DynamicsCollisionModule.NUMERICAL_SOLVER_ID, DynamicsCollisionModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                SpringOscillationModule.NUMERICAL_SOLVER_ID, SpringOscillationModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                PositionTimeGraphModule.NUMERICAL_SOLVER_ID, PositionTimeGraphModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                VelocityTimeGraphModule.NUMERICAL_SOLVER_ID, VelocityTimeGraphModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                AccelerationTimeGraphModule.NUMERICAL_SOLVER_ID,
                AccelerationTimeGraphModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                RadioSignalChainModule.NUMERICAL_SOLVER_ID, RadioSignalChainModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                RadioCommunicationModule.NUMERICAL_SOLVER_ID, RadioCommunicationModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                UltrasoundImagingModule.NUMERICAL_SOLVER_ID, UltrasoundImagingModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                EnergyEnvironmentModule.NUMERICAL_SOLVER_ID, EnergyEnvironmentModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertTrue(registry.supportsPair(
                ExperimentalDataGraphModule.NUMERICAL_SOLVER_ID,
                ExperimentalDataGraphModule.REFERENCE_SOLVER_ID));
        org.junit.jupiter.api.Assertions.assertFalse(registry.supportsPair("legacy_solver", "legacy_reference"));
        BoundPhysicsModule bound = registry.bind(
                AcWaveformModule.NUMERICAL_SOLVER_ID,
                AcWaveformModule.REFERENCE_SOLVER_ID,
                quantities);

        assertEquals(AcWaveformModule.MODULE_ID, bound.moduleId());
        assertEquals(74, registry.size());
    }

    @Test
    void rejectsMissingAndMismatchedBindings() {
        PhysicsModuleRegistry registry = new PhysicsModuleRegistry(List.of(
                new AcWaveformModule(), new OtherModule()));

        assertThrows(SolverBindingException.class, () -> registry.bind("missing", "ac_waveform_reference", quantities));
        assertThrows(SolverBindingException.class, () -> registry.bind(
                AcWaveformModule.NUMERICAL_SOLVER_ID, OtherModule.REFERENCE_ID, quantities));
        assertThrows(SolverBindingException.class, () -> registry.bindModule("missing", quantities));
        assertThrows(SolverBindingException.class, () -> registry.supportsPair(
                AcWaveformModule.NUMERICAL_SOLVER_ID, OtherModule.REFERENCE_ID));
    }

    @Test
    void rejectsDuplicateModuleAndSolverIds() {
        assertThrows(IllegalStateException.class, () -> new PhysicsModuleRegistry(List.of(
                new AcWaveformModule(), new AcWaveformModule())));
        assertThrows(IllegalStateException.class, () -> new PhysicsModuleRegistry(List.of(
                new AcWaveformModule(), new DuplicateNumericalModule())));
    }

    private static class OtherModule implements PhysicsModule<AcWaveformParametersForTest> {
        private static final String REFERENCE_ID = "other_reference";

        @Override public String moduleId() { return "other_module"; }
        @Override public String numericalSolverId() { return "other_numerical"; }
        @Override public String referenceSolverId() { return REFERENCE_ID; }
        @Override public AcWaveformParametersForTest bind(CanonicalQuantityBag values) { return new AcWaveformParametersForTest(); }
        @Override public com.example.backend.physics.model.SolverOutput solve(AcWaveformParametersForTest p, SimulationClock clock) { return null; }
        @Override public com.example.backend.physics.model.AnalyticalPoint referenceAt(AcWaveformParametersForTest p, double time) { return null; }
    }

    private static final class DuplicateNumericalModule extends OtherModule {
        @Override public String numericalSolverId() { return AcWaveformModule.NUMERICAL_SOLVER_ID; }
    }

    private static final class AcWaveformParametersForTest { }
}
