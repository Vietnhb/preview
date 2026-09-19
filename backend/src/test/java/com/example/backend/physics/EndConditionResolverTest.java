package com.example.backend.physics;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class EndConditionResolverTest {
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void normalizesLegacyDurationToTimeLimit() throws Exception {
        var spec = mapper.readTree("{\"duration\":10}");
        var condition = EndConditionResolver.normalize(spec, 20);
        assertEquals("time_limit", condition.path("type").asText());
        assertEquals(10, condition.path("duration").asDouble());
    }

    @Test
    void resolvesAndTrimsThresholdAtInterpolatedTime() throws Exception {
        var condition = mapper.readTree("{\"type\":\"threshold\",\"quantity\":\"positions.x\",\"operator\":\">=\",\"value\":10}");
        var output = new SolverOutput(List.of(0d, 1d, 2d), Map.of("x", List.of(1d, 5d, 13d)), Map.of(), Map.of(), Map.of());
        var end = EndConditionResolver.resolve(condition, output);
        var trimmed = EndConditionResolver.trim(output, end.time());
        assertEquals(1.625, end.time(), 1e-9);
        assertTrue(end.conditionReached());
        assertEquals(1.625, trimmed.time().get(trimmed.time().size() - 1), 1e-9);
        assertEquals(10, trimmed.positions().get("x").get(trimmed.positions().get("x").size() - 1), 1e-9);
    }

    @Test
    void resolvesContactWithoutTreatingZeroXAsGroundContact() throws Exception {
        var condition = mapper.readTree("{\"type\":\"event\",\"event\":{\"type\":\"contact\",\"entities\":[\"ball\",\"ground\"],\"quantity\":\"positions.y\",\"operator\":\"<=\",\"value\":0}}");
        var output = new SolverOutput(List.of(0d, 1d, 2d),
                Map.of("x", List.of(0d, 2d, 4d), "y", List.of(4d, 1d, -1d)), Map.of(), Map.of(), Map.of());
        var end = EndConditionResolver.resolve(condition, output);
        assertEquals(1.5, end.time(), 1e-9);
        assertTrue(end.conditionReached());
    }

    @Test
    void resolvesProjectileContactFromSolverOutput() throws Exception {
        var specification = mapper.readTree("{\"model\":\"projectile_2d\",\"initial_position\":0,\"initial_height\":10,\"initial_velocity\":4,\"launch_angle\":0}");
        var output = new KinematicsSolver().solve(specification, Map.of(), 10, 0.05);
        var condition = mapper.readTree("{\"type\":\"event\",\"event\":{\"type\":\"contact\",\"entities\":[\"ball\",\"ground\"],\"quantity\":\"positions.y\",\"operator\":\"<=\",\"value\":0}}");
        var end = EndConditionResolver.resolve(condition, output);
        assertEquals(Math.sqrt(20 / 9.81), end.time(), 0.05);
        assertTrue(end.conditionReached());
    }

    @Test
    void contactAtLaunchSurfaceWaitsForTheObjectToReturn() throws Exception {
        var specification = mapper.readTree("{\"model\":\"projectile_2d\",\"initial_position\":0,\"initial_height\":0,\"initial_velocity\":10,\"launch_angle\":1.5707963267948966}");
        var output = new KinematicsSolver().solve(specification, Map.of(), 4, 0.05);
        var condition = mapper.readTree("{\"type\":\"event\",\"event\":{\"type\":\"contact\",\"entities\":[\"ball\",\"ground\"],\"quantity\":\"positions.y\",\"operator\":\"<=\",\"value\":0}}");

        var end = EndConditionResolver.resolve(condition, output);

        assertEquals(20 / 9.81, end.time(), 0.05);
        assertTrue(end.conditionReached());
    }

    @Test
    void resolvesTargetAgainAfterVelocityChanges() throws Exception {
        var specification = mapper.readTree("{\"model\":\"uniform_acceleration_1d\",\"initial_position\":1,\"initial_velocity\":1,\"acceleration\":0}");
        var condition = mapper.readTree("{\"type\":\"threshold\",\"quantity\":\"position.x\",\"operator\":\">=\",\"value\":10}");
        var first = EndConditionResolver.resolve(condition, new KinematicsSolver().solve(specification, Map.of(), 10, 0.05));
        var second = EndConditionResolver.resolve(condition, new KinematicsSolver().solve(specification, Map.of("initial_velocity", 0.5), 20, 0.05));
        assertEquals(9, first.time(), 0.05);
        assertEquals(18, second.time(), 0.05);
    }

    @Test
    void resolvesCollisionFromGenericPositionPair() throws Exception {
        var condition = mapper.readTree("{\"type\":\"event\",\"event\":{\"type\":\"collision\",\"entities\":[\"x1\",\"x2\"]}}");
        var output = new SolverOutput(List.of(0d, 1d, 2d),
                Map.of("x1", List.of(0d, 1d, 2d), "x2", List.of(3d, 2d, 1d)), Map.of(), Map.of(), Map.of());
        var end = EndConditionResolver.resolve(condition, output);
        assertEquals(1.5, end.time(), 1e-9);
        assertTrue(end.conditionReached());
    }

    @Test
    void doesNotGuessCollisionFromUnrelatedPositionSeries() throws Exception {
        var condition = mapper.readTree("{\"type\":\"event\",\"event\":{\"type\":\"collision\",\"entities\":[\"left\",\"right\"]}}");
        var output = new SolverOutput(List.of(0d, 1d, 2d),
                Map.of("x1", List.of(0d, 1d, 2d), "x2", List.of(3d, 2d, 1d)), Map.of(), Map.of(), Map.of());

        var end = EndConditionResolver.resolve(condition, output);

        assertEquals(2, end.time(), 1e-9);
        assertEquals("max_time", end.reason());
        assertFalse(end.conditionReached());
    }

    @Test
    void doesNotInterpretCollisionPositionAsAnEventTime() throws Exception {
        var condition = mapper.readTree("{\"type\":\"event\",\"event\":{\"type\":\"collision\",\"entities\":[\"left\",\"right\"],\"firstQuantity\":\"positions.x1\",\"secondQuantity\":\"positions.x2\"}}");
        var output = new SolverOutput(List.of(0d, 1d, 2d),
                Map.of("x1", List.of(0d, 1d, 2d), "x2", List.of(10d, 11d, 12d)), Map.of(), Map.of(),
                Map.of("collisionTime", List.of(10d), "collisionX", List.of(1d)));

        var end = EndConditionResolver.resolve(condition, output);

        assertEquals(2, end.time(), 1e-9);
        assertFalse(end.conditionReached());
    }

    @Test
    void rejectsContactConditionsWithoutAnObservableThreshold() throws Exception {
        var condition = mapper.readTree("{\"type\":\"event\",\"event\":{\"type\":\"contact\",\"entities\":[\"ball\",\"ground\"]}}");

        assertTrue(EndConditionResolver.validateNode(condition, 10).stream()
                .anyMatch(error -> error.contains("requires quantity, operator and value")));
    }

    @Test
    void resolvesFiveCyclesFromObservedSeries() throws Exception {
        var condition = mapper.readTree("{\"type\":\"cycle_count\",\"quantity\":\"oscillation\",\"count\":5}");
        List<Double> times = java.util.stream.DoubleStream.iterate(0, value -> value + 0.05).limit(241)
                .boxed().toList();
        List<Double> values = times.stream().map(time -> Math.cos(Math.PI * time)).toList();
        var output = new SolverOutput(times, Map.of(), Map.of(), Map.of(), Map.of("x", values));
        var end = EndConditionResolver.resolve(condition, output);
        assertEquals(10, end.time(), 0.1);
        assertTrue(end.conditionReached());
    }

    @Test
    void returnsMaxTimeWhenThresholdIsNotReached() throws Exception {
        var condition = mapper.readTree("{\"type\":\"threshold\",\"quantity\":\"positions.x\",\"operator\":\">=\",\"value\":100,\"maxTime\":2}");
        var output = new SolverOutput(List.of(0d, 1d, 2d, 3d), Map.of("x", List.of(0d, 1d, 2d, 3d)), Map.of(), Map.of(), Map.of());
        var end = EndConditionResolver.resolve(condition, output);
        assertEquals(2, end.time(), 1e-9);
        assertEquals("max_time", end.reason());
        assertFalse(end.conditionReached());
    }
}
