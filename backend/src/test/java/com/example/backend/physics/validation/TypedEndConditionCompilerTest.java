package com.example.backend.physics.validation;

import com.example.backend.physics.model.SolverOutput;
import com.example.backend.service.problem.CompiledSchema;
import com.example.backend.service.problem.SchemaCompiler;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class TypedEndConditionCompilerTest {
    private static final double EPSILON = 1e-9;
    private final ObjectMapper mapper = new ObjectMapper();

    @Test
    void compilesPinnedTimeLimitAndThresholdSourcesAgainstDeclaredSeries() throws Exception {
        CompiledSchema schema = schema();
        SolverOutput output = output(List.of(0d, 1d, 2d), Map.of("signal", List.of(0d, 4d, 8d)));

        EndConditionContract timeLimit = TypedEndConditionCompiler.compile(schema,
                request("{\"type\":\"time_limit\",\"duration\":1.5}"), 2);
        assertEquals(1.5, EndConditionResolver.resolve(timeLimit, output).time(), EPSILON);

        EndConditionContract threshold = TypedEndConditionCompiler.compile(schema,
                request("{\"type\":\"threshold\",\"quantity\":\"values.signal\",\"operator\":\">=\",\"value\":5}"), 2);
        assertTrue(threshold instanceof EndConditionContract.Threshold bound
                && bound.source().equals(OutputSourceBinding.declared(OutputSourceBinding.Group.VALUES, "signal")));
        assertEquals(1.25, EndConditionResolver.resolve(threshold, output).time(), EPSILON);
    }

    @Test
    void compilesCycleCountAndContactEventSourcesFromTheSamePinnedOutputMap() throws Exception {
        CompiledSchema schema = schema();
        SolverOutput output = output(List.of(0d, 1d, 2d, 3d, 4d, 5d, 6d, 7d, 8d), Map.of(
                "signal", List.of(0d, 1d, 0d, -1d, 0d, 1d, 0d, -1d, 0d),
                "gap", List.of(3d, 1d, -1d, -1d, -1d, -1d, -1d, -1d, -1d)));

        EndConditionContract cycle = TypedEndConditionCompiler.compile(schema,
                request("{\"type\":\"cycle_count\",\"quantity\":\"signal\",\"count\":1}"), 8);
        assertEquals(4d, EndConditionResolver.resolve(cycle, output).time(), EPSILON);

        EndConditionContract event = TypedEndConditionCompiler.compile(schema, request("""
                {"type":"event","event":{"type":"contact","entities":["body-a","body-b"],
                 "quantity":"values.gap","operator":"<=","value":0}}
                """), 8);
        assertEquals(1.5, EndConditionResolver.resolve(event, output).time(), EPSILON);
    }

    @Test
    void rejectsUndeclaredAndWrongGroupSourcesInsteadOfRuntimeGuessing() throws Exception {
        CompiledSchema schema = schema();
        JsonNode unknown = request("{\"type\":\"threshold\",\"quantity\":\"missing\",\"operator\":\">\",\"value\":0}");
        JsonNode wrongGroup = request("{\"type\":\"threshold\",\"quantity\":\"positions.signal\",\"operator\":\">\",\"value\":0}");

        assertThrows(IllegalArgumentException.class,
                () -> TypedEndConditionCompiler.compile(schema, unknown, 2));
        assertThrows(IllegalArgumentException.class,
                () -> TypedEndConditionCompiler.compile(schema, wrongGroup, 2));
    }

    private CompiledSchema schema() throws Exception {
        JsonNode definition = mapper.readTree("""
                {
                  "version":"2.1","topic":"TEST","model":"sample",
                  "requiredQuantities":[],"optionalQuantities":[],
                  "execution":{"durationSeconds":2,"stepSeconds":1},
                  "validation":{"tolerance":0,"checkpointFractions":[]},
                  "output":{"type":"timeseries","probeSeries":["signal","gap"],"definitions":[
                    {"key":"signal","kind":"time_series","unit":"m","required":true},
                    {"key":"gap","kind":"time_series","unit":"m","required":true}
                  ]}
                }
                """);
        return new SchemaCompiler(mapper).compile(definition, "sample_schema", "2.1", "TEST");
    }

    private JsonNode request(String endCondition) throws Exception {
        return mapper.readTree("{\"endCondition\":" + endCondition + "}");
    }

    private static SolverOutput output(List<Double> time, Map<String, List<Double>> values) {
        return new SolverOutput(time, Map.of(), Map.of(), Map.of(), values);
    }
}
