package com.example.backend.physics;

import com.example.backend.physics.output.PhysicsOutput;
import com.example.backend.physics.output.PhysicsOutputContract;
import com.example.backend.physics.output.PhysicsOutputFrame;
import com.example.backend.physics.output.PhysicsOutputValidator;
import com.example.backend.physics.output.ScalarOutput;
import com.example.backend.physics.output.ScalarFieldOutput;
import com.example.backend.physics.output.TimeSeriesOutput;
import com.example.backend.physics.output.VectorSeriesOutput;
import com.example.backend.physics.model.ScalarField;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PhysicsOutputValidatorTest {
    private static final int MAX_SAMPLES = 10;

    @Test
    void validatesTypedScalarAndTimeSeriesAgainstPinnedUnitsAndKinds() {
        PhysicsOutputContract contract = contract(Map.of(
                "energy", new PhysicsOutputContract.OutputDefinition(PhysicsOutput.OutputKind.SCALAR, "J", true),
                "position", new PhysicsOutputContract.OutputDefinition(PhysicsOutput.OutputKind.TIME_SERIES, "m", true)));
        PhysicsOutputFrame frame = new PhysicsOutputFrame(List.of(0.0, 1.0), List.of(
                new ScalarOutput("energy", "J", 12.0),
                new TimeSeriesOutput("position", "m", List.of(0.0, 1.0), List.of(0.0, 2.0))));

        assertDoesNotThrow(() -> PhysicsOutputValidator.validate(contract, frame));
    }

    @Test
    void rejectsMissingAndUndeclaredOutputsAndWrongUnitOrKind() {
        PhysicsOutputContract contract = contract(Map.of(
                "position", new PhysicsOutputContract.OutputDefinition(PhysicsOutput.OutputKind.TIME_SERIES, "m", true)));
        assertThrows(IllegalArgumentException.class, () -> PhysicsOutputValidator.validate(contract,
                new PhysicsOutputFrame(List.of(0.0), List.of())));
        assertThrows(IllegalArgumentException.class, () -> PhysicsOutputValidator.validate(contract,
                new PhysicsOutputFrame(List.of(0.0), List.of(new ScalarOutput("position", "m", 2.0)))));
        assertThrows(IllegalArgumentException.class, () -> PhysicsOutputValidator.validate(contract,
                new PhysicsOutputFrame(List.of(0.0), List.of(new ScalarOutput("other", "m", 2.0)))));
        assertThrows(IllegalArgumentException.class, () -> PhysicsOutputValidator.validate(contract,
                new PhysicsOutputFrame(List.of(0.0), List.of(new TimeSeriesOutput("position", "cm",
                        List.of(0.0), List.of(2.0))))));
    }

    @Test
    void rejectsDuplicateKeysInvalidTimeAndMalformedVectorShape() {
        PhysicsOutputContract contract = contract(Map.of(
                "position", new PhysicsOutputContract.OutputDefinition(PhysicsOutput.OutputKind.TIME_SERIES, "m", true)));
        assertThrows(IllegalArgumentException.class, () -> PhysicsOutputValidator.validate(contract,
                new PhysicsOutputFrame(List.of(0.0, 0.0), List.of(
                        new TimeSeriesOutput("position", "m", List.of(0.0, 0.0), List.of(1.0, 2.0))))));
        assertThrows(IllegalArgumentException.class, () -> PhysicsOutputValidator.validate(contract,
                new PhysicsOutputFrame(List.of(0.0), List.of(
                        new TimeSeriesOutput("position", "m", List.of(0.0), List.of(1.0)),
                        new TimeSeriesOutput("position", "m", List.of(0.0), List.of(2.0))))));
        assertThrows(IllegalArgumentException.class, () -> new VectorSeriesOutput("velocity", "m/s",
                List.of(0.0), List.of("x", "y"), List.of(List.of(1.0))));
    }

    @Test
    void validatesVectorAndScalarFieldAxesAndUnits() {
        PhysicsOutputContract contract = contract(Map.of(
                "velocity", new PhysicsOutputContract.OutputDefinition(PhysicsOutput.OutputKind.VECTOR_SERIES, "m/s", true),
                "wave", new PhysicsOutputContract.OutputDefinition(PhysicsOutput.OutputKind.SCALAR_FIELD, "m", true)));
        ScalarField field = new ScalarField(ScalarField.CONTRACT_VERSION, ScalarField.TYPE, 1,
                List.of(new ScalarField.Axis("x", "m", List.of(0.0, 1.0, 2.0))),
                List.of(2, 3), List.of(0.0, 1.0), List.of(List.of(0.0, 0.5, 1.0), List.of(0.1, 0.6, 1.1)),
                "m", "s", new ScalarField.Sampling(1.0, 1.0), "linear", "fixed");
        PhysicsOutputFrame frame = new PhysicsOutputFrame(List.of(0.0, 1.0), List.of(
                new VectorSeriesOutput("velocity", "m/s", List.of(0.0, 1.0), List.of("x", "y"),
                        List.of(List.of(1.0, 2.0), List.of(3.0, 4.0))),
                new ScalarFieldOutput("wave", field)));

        assertDoesNotThrow(() -> PhysicsOutputValidator.validate(contract, frame));
        assertThrows(IllegalArgumentException.class, () -> PhysicsOutputValidator.validate(contract,
                new PhysicsOutputFrame(List.of(0.0), List.of(new ScalarFieldOutput("wave", field)))));
    }

    @Test
    void permitsOptionalDeclaredOutputButRejectsOtherKeys() {
        PhysicsOutputContract contract = contract(Map.of(
                "temperature", new PhysicsOutputContract.OutputDefinition(PhysicsOutput.OutputKind.SCALAR, "K", false)));
        assertDoesNotThrow(() -> PhysicsOutputValidator.validate(contract,
                new PhysicsOutputFrame(List.of(0.0), List.of())));
        assertThrows(IllegalArgumentException.class, () -> PhysicsOutputValidator.validate(contract,
                new PhysicsOutputFrame(List.of(0.0), List.of(new ScalarOutput("energy", "J", 4.0)))));
        assertThrows(IllegalArgumentException.class, () -> PhysicsOutputValidator.validate(contract,
                new PhysicsOutputFrame(List.of(0.0), List.of(new ScalarOutput("temperature", Optional.empty(), 280.0)))));
    }

    private PhysicsOutputContract contract(Map<String, PhysicsOutputContract.OutputDefinition> outputs) {
        return new PhysicsOutputContract("schema", "2.0", "model", outputs, MAX_SAMPLES);
    }
}
