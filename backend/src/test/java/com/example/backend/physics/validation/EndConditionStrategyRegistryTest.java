package com.example.backend.physics.validation;

import com.example.backend.physics.model.ScalarField;
import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.output.PhysicsOutputFrame;
import com.example.backend.physics.output.TimeSeriesOutput;
import com.example.backend.physics.output.VectorSeriesOutput;
import com.fasterxml.jackson.databind.node.JsonNodeFactory;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndConditionStrategyRegistryTest {
    private static final double EPSILON = 1e-9;
    @Test
    void timeLimitStrategyInterpolatesAtTheRequestedHorizon() {
        SolverOutput output = output(List.of(0d, 1d, 2d), Map.of("x", List.of(0d, 2d, 4d)));
        var result = EndConditionResolver.resolve(new EndConditionContract.TimeLimit(1.5), output);
        assertEquals(1.5, result.time(), EPSILON);
        assertTrue(result.conditionReached());
        assertEquals("time_limit", result.reason());
    }

    @Test
    void thresholdStrategyUsesTypedOutputGroupAndDeterministicInterpolation() {
        SolverOutput output = output(List.of(0d, 1d, 2d), Map.of("x", List.of(0d, 4d, 8d)));
        var condition = new EndConditionContract.Threshold(
                OutputSourceBinding.declared(OutputSourceBinding.Group.VALUES, "x"),
                ComparisonOperator.GREATER_OR_EQUAL, 5, null);
        var result = EndConditionResolver.resolve(condition, output);
        assertEquals(1.25, result.time(), EPSILON);
        assertTrue(result.conditionReached());
    }

    @Test
    void typedFrameResolutionUsesDeclaredOutputValuesDirectly() {
        List<Double> time = List.of(0d, 1d, 2d);
        PhysicsOutputFrame frame = new PhysicsOutputFrame(time,
                List.of(new TimeSeriesOutput("x", "m", time, List.of(0d, 4d, 8d))));
        var condition = new EndConditionContract.Threshold(
                OutputSourceBinding.declared(OutputSourceBinding.Group.VALUES, "x"),
                ComparisonOperator.GREATER_OR_EQUAL, 5, null);
        var result = EndConditionResolver.resolve(condition, frame);
        assertEquals(1.25, result.time(), EPSILON);
        assertTrue(result.conditionReached());
    }

    @Test
    void eventStrategyUsesDeclaredContactSourceAndBoundaryCrossing() {
        SolverOutput output = output(List.of(0d, 1d, 2d), Map.of("gap", List.of(3d, 1d, -1d)));
        var condition = new EndConditionContract.Event(EndConditionContract.EventKind.CONTACT,
                List.of("body-a", "body-b"),
                OutputSourceBinding.declared(OutputSourceBinding.Group.VALUES, "gap"),
                ComparisonOperator.LESS_OR_EQUAL, 0d, null, null, null, null);
        var result = EndConditionResolver.resolve(condition, output);
        assertEquals(1.5, result.time(), EPSILON);
        assertEquals("event", result.reason());
        assertTrue(result.conditionReached());
    }

    @Test
    void cycleCountStrategyEstimatesPeriodFromCrossings() {
        SolverOutput output = output(List.of(0d, 1d, 2d, 3d, 4d, 5d, 6d, 7d, 8d),
                Map.of("signal", List.of(0d, 1d, 0d, -1d, 0d, 1d, 0d, -1d, 0d)));
        var condition = new EndConditionContract.CycleCount(
                OutputSourceBinding.declared(OutputSourceBinding.Group.VALUES, "signal"), 1, null);
        var result = EndConditionResolver.resolve(condition, output);
        assertEquals(4d, result.time(), EPSILON);
        assertTrue(result.conditionReached());
    }

    @Test
    void manualStrategyRemainsBoundedByItsCompiledMaximum() {
        SolverOutput output = output(List.of(0d, 1d, 2d), Map.of("x", List.of(0d, 1d, 2d)));
        var result = EndConditionResolver.resolve(new EndConditionContract.Manual(1.25), output);
        assertEquals(1.25, result.time(), EPSILON);
        assertFalse(result.conditionReached());
        assertEquals("max_time", result.reason());
    }

    @Test
    void legacyJsonAdapterCompilesOnceAndKeepsLegacyUnqualifiedLookupAtBoundary() {
        var json = JsonNodeFactory.instance.objectNode().put("type", "threshold").put("quantity", "x")
                .put("operator", ">=").put("value", 1.5);
        var contract = LegacyEndConditionJsonAdapter.compile(json, 2);
        assertTrue(contract instanceof EndConditionContract.Threshold threshold
                && threshold.source().group() == OutputSourceBinding.Group.LEGACY_AUTO);
        SolverOutput output = output(List.of(0d, 1d, 2d), Map.of("x", List.of(0d, 1d, 2d)));
        assertEquals(1.5, EndConditionResolver.resolve(contract, output).time(), EPSILON);
    }

    @Test
    void legacyCollisionEntityMatchingIsCompiledIntoCompatibilityOnlySourceBindings() {
        var json = JsonNodeFactory.instance.objectNode().put("type", "event");
        json.putObject("event").put("type", "collision").putArray("entities").add("balla").add("wall");
        var contract = LegacyEndConditionJsonAdapter.compile(json, 2);
        var event = (EndConditionContract.Event) contract;
        assertEquals(OutputSourceBinding.Group.LEGACY_ENTITY_POSITION, event.firstSource().group());
        SolverOutput output = new SolverOutput(List.of(0d, 1d, 2d),
                Map.of("Ball-A", List.of(2d, 1d, 0d), "wall", List.of(0d, 0d, 0d)),
                Map.of(), Map.of(), Map.of());
        assertEquals(2d, EndConditionResolver.resolve(contract, output).time(), EPSILON);
    }

    @Test
    void legacyAdapterRejectsUnknownTypesAndTypedBindingsCannotUseAutoLookup() {
        var bad = JsonNodeFactory.instance.objectNode().put("type", "eventual");
        assertThrows(IllegalArgumentException.class, () -> LegacyEndConditionJsonAdapter.compile(bad, 2));
        assertThrows(IllegalArgumentException.class,
                () -> OutputSourceBinding.declared(OutputSourceBinding.Group.LEGACY_AUTO, "x"));
        assertThrows(IllegalArgumentException.class,
                () -> new EndConditionContract.Manual(EndConditionResolver.MAX_DYNAMIC_SECONDS + 1));
        assertThrows(IllegalArgumentException.class,
                () -> new EndConditionContract.Event(EndConditionContract.EventKind.COLLISION,
                        List.of("a", "b"), null, null, null,
                        OutputSourceBinding.declared(OutputSourceBinding.Group.VALUES, "a"), null, null, null));
    }

    @Test
    void registryRejectsDuplicateMissingAndMismatchedStrategyBindings() {
        EndConditionStrategy<EndConditionContract.TimeLimit> timeLimit = strategy(
                EndConditionType.TIME_LIMIT, EndConditionContract.TimeLimit.class);
        assertThrows(IllegalArgumentException.class,
                () -> new EndConditionStrategyRegistry(List.of(timeLimit, timeLimit)));
        assertThrows(IllegalArgumentException.class, () -> new EndConditionStrategyRegistry(List.of()));
        EndConditionStrategy<EndConditionContract.Manual> mismatched = strategy(
                EndConditionType.TIME_LIMIT, EndConditionContract.Manual.class);
        assertThrows(IllegalArgumentException.class,
                () -> new EndConditionStrategyRegistry(List.of(mismatched)));
    }

    @Test
    void trimmingPreservesStandaloneScalarsAndAllTimelineSeriesShapes() {
        List<Double> time = List.of(0d, 1d, 2d);
        ScalarField field = new ScalarField(1, "scalarField", 1,
                List.of(new ScalarField.Axis("x", "m", List.of(0d, 1d))), List.of(3, 2), time,
                List.of(List.of(0d, 1d), List.of(2d, 3d), List.of(4d, 5d)), "V", "s",
                new ScalarField.Sampling(1, 1), "linear", "fixed");
        SolverOutput source = new SolverOutput(time,
                Map.of("x", List.of(0d, 1d, 2d)),
                Map.of("x", List.of(2d, 2d, 2d)),
                Map.of("x", List.of(0d, 0d, 0d)),
                Map.of("x", List.of(0d, 1d, 2d), "potential", List.of(0d, 10d, 20d)), Map.of("field", field),
                Map.of("doseRate", 2.0));

        SolverOutput trimmed = EndConditionResolver.trim(source, 1.5);
        assertEquals(List.of(0d, 1d, 1.5), trimmed.time());
        assertEquals(List.of(0d, 1d, 1.5), trimmed.positions().get("x"));
        assertEquals(List.of(2d, 2d, 2d), trimmed.velocities().get("x"));
        assertEquals(List.of(0d, 0d, 0d), trimmed.accelerations().get("x"));
        assertEquals(List.of(0d, 10d, 15d), trimmed.values().get("potential"));
        assertEquals(Map.of("doseRate", 2.0), trimmed.scalarOutputs());
        ScalarField actual = trimmed.scalarFields().get("field");
        assertEquals(List.of(0d, 1d, 1.5), actual.time());
        assertEquals(List.of(3, 2), actual.shape());
        assertEquals(List.of(List.of(0d, 1d), List.of(2d, 3d), List.of(3d, 4d)), actual.values());
        SolverOutput acceptedTrim = new SolverOutput(trimmed.time(), Map.of(), Map.of(), Map.of(),
                Map.of("potential", trimmed.values().get("potential")));
        assertDoesNotThrow(() -> OutputContractValidator.validate("trimmed",
                JsonNodeFactory.instance.objectNode().putObject("output").putArray("probeSeries")
                .add("potential"), acceptedTrim));
    }

    @Test
    void typedTrimPreservesVectorShapeAndInterpolatesFinalSample() {
        List<Double> time = List.of(0d, 1d, 2d);
        PhysicsOutputFrame source = new PhysicsOutputFrame(time, List.of(
                new VectorSeriesOutput("velocity", "m/s", time, List.of("x", "y"),
                        List.of(List.of(0d, 0d), List.of(2d, 4d), List.of(4d, 8d)))));
        PhysicsOutputFrame trimmed = EndConditionResolver.trim(source, 1.5d);
        assertEquals(List.of(0d, 1d, 1.5d), trimmed.timeSeconds());
        VectorSeriesOutput velocity = (VectorSeriesOutput) trimmed.outputs().getFirst();
        assertEquals(List.of(List.of(0d, 0d), List.of(2d, 4d), List.of(3d, 6d)), velocity.values());
        assertEquals(List.of("x", "y"), velocity.componentKeys());
    }

    private static SolverOutput output(List<Double> time, Map<String, List<Double>> values) {
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }

    private static <T extends EndConditionContract> EndConditionStrategy<T> strategy(EndConditionType type,
            Class<T> contractType) {
        return new EndConditionStrategy<>() {
            @Override public EndConditionType type() { return type; }
            @Override public Class<T> contractType() { return contractType; }
            @Override public EndConditionResolver.ResolvedEnd resolve(T condition, SolverOutput output, double limit) {
                return new EndConditionResolver.ResolvedEnd(limit, "test", false);
            }
        };
    }
}
