package com.example.backend.service.problem;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

class SchemaCompilerOutputValidationTest {
    private final ObjectMapper mapper = new ObjectMapper();
    private final SchemaCompiler compiler = new SchemaCompiler(mapper);

    @Test
    void compilesPerOutputNumericAndDiscreteComparisonContracts() throws Exception {
        var definition = mapper.readTree("""
                {
                  "model":"test_model",
                  "execution":{"durationSeconds":1,"stepSeconds":0.1},
                  "validation":{"tolerance":0.001,"checkpointFractions":[0.5,1],
                    "outputs":{
                      "force":{"absoluteTolerance":0.000001,"relativeTolerance":0.002},
                      "state":{"absoluteTolerance":0,"relativeTolerance":0,"comparison":"discrete"}
                    }},
                  "output":{"probeSeries":["force","state"]}
                }
                """);

        var compiled = compiler.compile(definition, "test-schema");

        assertEquals(new CompiledSchema.OutputValidationDefinition(0.000001, 0.002, "numeric"),
                compiled.validation().outputTolerances().get("force"));
        assertEquals(new CompiledSchema.OutputValidationDefinition(0, 0, "discrete"),
                compiled.validation().outputTolerances().get("state"));
    }

    @Test
    void rejectsNegativeOrUnknownOutputComparisonContracts() throws Exception {
        var negative = mapper.readTree("""
                {"model":"test_model","execution":{"durationSeconds":1,"stepSeconds":0.1},
                 "validation":{"tolerance":0.001,"checkpointFractions":[1],
                   "outputs":{"force":{"absoluteTolerance":-1}}},
                 "output":{"probeSeries":["force"]}}
                """);
        var unknown = mapper.readTree("""
                {"model":"test_model","execution":{"durationSeconds":1,"stepSeconds":0.1},
                 "validation":{"tolerance":0.001,"checkpointFractions":[1],
                   "outputs":{"force":{"comparison":"approximate"}}},
                 "output":{"probeSeries":["force"]}}
                """);

        assertThrows(IllegalArgumentException.class, () -> compiler.compile(negative, "test-schema"));
        assertThrows(IllegalArgumentException.class, () -> compiler.compile(unknown, "test-schema"));
    }

    @Test
    void rejectsUnitsMissingFromSharedUnitCatalog() throws Exception {
        var unknownQuantityUnit = mapper.readTree("""
                {"model":"test_model","requiredQuantities":[{"key":"mass","allowedUnits":["widgets"]}],
                 "execution":{"durationSeconds":1,"stepSeconds":0.1},
                 "validation":{"tolerance":0.001,"checkpointFractions":[1]},
                 "output":{"probeSeries":["force"]}}
                """);
        var unknownOutputUnit = mapper.readTree("""
                {"model":"test_model","execution":{"durationSeconds":1,"stepSeconds":0.1},
                 "validation":{"tolerance":0.001,"checkpointFractions":[1]},
                 "output":{"definitions":[{"key":"force","kind":"scalar","unit":"widgets","required":true}]}}
                """);

        assertThrows(IllegalArgumentException.class, () -> compiler.compile(unknownQuantityUnit, "test-schema"));
        assertThrows(IllegalArgumentException.class, () -> compiler.compile(unknownOutputUnit, "test-schema"));
    }
}
