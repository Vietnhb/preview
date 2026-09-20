package com.example.backend.physics;

import com.example.backend.physics.model.SolverOutput;
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
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/** Golden checks for the cross-grade formula families added to the catalog. */
class CurriculumPhysicsExpansionTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test void calorimetryConservesEnergy() {
        ObjectNode s = mapper.createObjectNode().put("model", "calorimetry_mixing").put("mass_1", 1).put("specific_heat_1", 1000).put("initial_temperature_1", 300).put("mass_2", 1).put("specific_heat_2", 1000).put("initial_temperature_2", 400);
        SolverOutput o = new CalorimetrySolver().solve(s, Map.of(), 1, .25);
        assertEquals(350, o.values().get("equilibriumTemperature").get(4), 1e-12);
        assertEquals(0, o.values().get("heat1").get(4) + o.values().get("heat2").get(4), 1e-9);
        assertEquals(350, new CalorimetryReferenceSolver().solve(s, Map.of(), .5).values().get("equilibriumTemperature"), 1e-12);
    }

    @Test void firstLawMatchesEnergyBalance() {
        ObjectNode s = mapper.createObjectNode().put("model", "first_law_thermodynamics").put("initial_internal_energy", 100).put("heat_added", 50).put("work_done", 20);
        SolverOutput o = new FirstLawSolver().solve(s, Map.of(), 1, .2);
        assertEquals(130, o.values().get("internalEnergy").get(5), 1e-12);
        assertEquals(30, o.values().get("deltaInternalEnergy").get(0), 1e-12);
        assertEquals(130, new FirstLawReferenceSolver().solve(s, Map.of(), 0).values().get("internalEnergy"), 1e-12);
    }

    @Test void snellLawAndTotalInternalReflection() {
        ObjectNode s = mapper.createObjectNode().put("model", "snell_refraction").put("refractive_index_1", 1).put("refractive_index_2", 1.5).put("incident_angle", Math.PI / 6);
        SolverOutput o = new RefractionSolver().solve(s, Map.of(), 1, .2);
        assertEquals(Math.asin(1.0 / 3), o.values().get("refractedAngle").get(0), 1e-12);
        ObjectNode tir = s.deepCopy().put("refractive_index_1", 1.5).put("refractive_index_2", 1).put("incident_angle", Math.PI / 3);
        assertTrue(new RefractionSolver().solve(tir, Map.of(), 1, .2).values().get("totalInternalReflection").get(0) > .5);
    }

    @Test void inductionAndAcRlcFormulaeMatchReferences() {
        ObjectNode induction = mapper.createObjectNode().put("model", "electromagnetic_induction").put("turns", 100).put("magnetic_field", .2).put("coil_area", .01).put("magnetic_field_rate", .5).put("coil_angle", 0);
        SolverOutput i = new InductionSolver().solve(induction, Map.of(), 1, .25);
        assertEquals(-.5, i.values().get("inducedEmf").get(0), 1e-12);
        assertEquals(i.values().get("inducedEmf").get(2), new InductionReferenceSolver().solve(induction, Map.of(), .5).values().get("inducedEmf"), 1e-12);
        ObjectNode ac = mapper.createObjectNode().put("model", "ac_rlc_circuit").put("resistance", 10).put("inductance", .1).put("capacitance", .001).put("frequency", 50).put("rms_voltage", 100);
        SolverOutput a = new AcRlcSolver().solve(ac, Map.of(), 1, .25);
        assertEquals(a.values().get("impedance").get(0), new AcRlcReferenceSolver().solve(ac, Map.of(), .5).values().get("impedance"), 1e-12);
    }

    @Test void quantumAndNuclearEnergyUseCanonicalConstants() {
        ObjectNode photo = mapper.createObjectNode().put("model", "photoelectric_effect").put("photon_frequency", 1e15).put("work_function", 1e-19);
        SolverOutput p = new PhotoelectricSolver().solve(photo, Map.of(), 1, .25);
        assertTrue(p.values().get("photonEnergy").get(0) > p.values().get("maximumKineticEnergy").get(0));
        ObjectNode nuclear = mapper.createObjectNode().put("model", "nuclear_energy").put("mass_defect", 1e-30);
        SolverOutput n = new NuclearEnergySolver().solve(nuclear, Map.of(), 1, .25);
        assertEquals(n.values().get("releasedEnergy").get(0), new NuclearEnergyReferenceSolver().solve(nuclear, Map.of(), .5).values().get("releasedEnergy"), 1e-12);
    }

    @Test void electricAndMagneticFieldFamiliesMatchReferences() {
        ObjectNode electric = mapper.createObjectNode().put("model", "point_charge_field").put("charge", 1e-6).put("distance", .1);
        SolverOutput e = new PointChargeFieldSolver().solve(electric, Map.of(), 1, .25);
        assertEquals(e.values().get("electricField").get(0), new PointChargeFieldReferenceSolver().solve(electric, Map.of(), .5).values().get("electricField"), 1e-12);
        ObjectNode magnetic = mapper.createObjectNode().put("model", "magnetic_force").put("charge", 2).put("speed", 3).put("magnetic_field", 4).put("velocity_field_angle", Math.PI / 2);
        SolverOutput m = new MagneticForceSolver().solve(magnetic, Map.of(), 1, .25);
        assertEquals(24, m.values().get("magneticForce").get(0), 1e-12);
        assertEquals(m.values().get("magneticForce").get(0), new MagneticForceReferenceSolver().solve(magnetic, Map.of(), .5).values().get("magneticForce"), 1e-12);
    }

    @Test void uncertaintyBoundsAreExplicit() {
        ObjectNode measurement = mapper.createObjectNode().put("model", "measurement_uncertainty").put("measured_value", 10).put("absolute_uncertainty", .2);
        SolverOutput output = new MeasurementUncertaintySolver().solve(measurement, Map.of(), 1, .25);
        assertEquals(.02, output.values().get("relativeUncertainty").get(0), 1e-12);
        assertEquals(9.8, output.values().get("lowerBound").get(4), 1e-12);
        assertEquals(output.values().get("upperBound").get(0), new MeasurementUncertaintyReferenceSolver().solve(measurement, Map.of(), .5).values().get("upperBound"), 1e-12);
    }

    @Test void experimentalGraphFitMatchesReference() {
        ObjectNode graph = mapper.createObjectNode().put("model", "experimental_data_graph")
                .put("x_start", 0).put("x_end", 4).put("slope", 2).put("intercept", 1).put("sample_count", 5);
        SolverOutput output = new ExperimentalGraphSolver().solve(graph, Map.of(), 1, .25);
        assertEquals(9, output.values().get("y").get(4), 1e-12);
        assertEquals(9, new ExperimentalGraphReferenceSolver().solve(graph, Map.of(), 4).values().get("y"), 1e-12);
        assertEquals(2, output.values().get("slope").get(2), 1e-12);
    }

    @Test void academicBoundaryLimitsAreRepresentedExplicitly() {
        ObjectNode gas = mapper.createObjectNode().put("model", "ideal_gas_isothermal")
                .put("amount_of_substance", 1).put("temperature", 300)
                .put("initial_volume", 1).put("volume_rate", 0);
        SolverOutput gasOutput = new IdealGasSolver().solve(gas, Map.of(), 1, .25);
        assertEquals(0, gasOutput.values().get("work").get(4), 1e-12);

        ObjectNode charge = mapper.createObjectNode().put("model", "point_charge_field")
                .put("charge", 0).put("distance", 1);
        SolverOutput chargeOutput = new PointChargeFieldSolver().solve(charge, Map.of(), 1, .25);
        assertEquals(0, chargeOutput.values().get("electricField").get(0), 1e-12);
        assertEquals(0, chargeOutput.values().get("electricPotential").get(0), 1e-12);

        ObjectNode lens = mapper.createObjectNode().put("model", "thin_lens_imaging")
                .put("focal_length", .1).put("object_distance", .2).put("object_height", 0);
        assertEquals(0, new ThinLensSolver().solve(lens, Map.of(), 1, .25)
                .values().get("imageHeight").get(0), 1e-12);

        ObjectNode stable = mapper.createObjectNode().put("model", "radioactive_decay")
                .put("initial_count", 100).put("decay_constant", 0);
        SolverOutput stableOutput = new RadioactiveDecaySolver().solve(stable, Map.of(), 1, .25);
        assertEquals(100, stableOutput.values().get("remainingCount").get(4), 1e-12);
        assertEquals(0, stableOutput.values().get("activity").get(4), 1e-12);

        ObjectNode threshold = mapper.createObjectNode().put("model", "photoelectric_effect")
                .put("photon_frequency", 1e14).put("work_function", 1e-19);
        SolverOutput thresholdOutput = new PhotoelectricSolver().solve(threshold, Map.of(), 1, .25);
        assertEquals(0, thresholdOutput.values().get("emissionOccurs").get(0), 1e-12);
        assertEquals(0, new PhotoelectricReferenceSolver().solve(threshold, Map.of(), .5)
                .values().get("emissionOccurs"), 1e-12);

        ObjectNode zeroMeasurement = mapper.createObjectNode().put("model", "measurement_uncertainty")
                .put("measured_value", 0).put("absolute_uncertainty", .2);
        SolverOutput zeroOutput = new MeasurementUncertaintySolver().solve(zeroMeasurement, Map.of(), 1, .25);
        assertEquals(0, zeroOutput.values().get("relativeUncertaintyDefined").get(0), 1e-12);
    }

    @Test void inductionRequiresAnIntegerTurnCount() {
        ObjectNode induction = mapper.createObjectNode().put("model", "electromagnetic_induction")
                .put("turns", 2.5).put("magnetic_field", .2).put("coil_area", .01)
                .put("magnetic_field_rate", .5).put("coil_angle", 0);
        assertThrows(IllegalArgumentException.class,
                () -> new InductionSolver().solve(induction, Map.of(), 1, .25));
    }
}
