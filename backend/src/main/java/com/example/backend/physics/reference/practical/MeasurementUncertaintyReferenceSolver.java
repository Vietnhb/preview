package com.example.backend.physics.reference.practical;

import com.example.backend.physics.reference.ReferenceSolver;

import com.example.backend.physics.model.*;
import com.example.backend.physics.model.modern.*;
import com.example.backend.physics.model.electromagnetism.*;
import com.example.backend.physics.model.dynamics.*;
import com.example.backend.physics.model.optics.*;
import com.example.backend.physics.model.thermal.*;
import com.example.backend.physics.model.waves.*;
import com.example.backend.physics.model.practical.*;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;
import java.util.Map;

@Component
public class MeasurementUncertaintyReferenceSolver implements ReferenceSolver {
    @Override public String solverId() { return "measurement_uncertainty_reference"; }
    @Override public AnalyticalPoint solve(JsonNode specification, Map<String, Double> overrides, double timeSeconds) {
        if (!"measurement_uncertainty".equals(PhysicsValues.model(specification))) throw new IllegalArgumentException("Unsupported measurement model: " + PhysicsValues.model(specification));
        MeasurementUncertaintyParameters p = MeasurementUncertaintyParameters.from(specification, overrides);
        return new AnalyticalPoint(Map.of("measuredValue",p.measuredValue(),"absoluteUncertainty",p.absoluteUncertainty(),"relativeUncertainty",p.relativeUncertainty(),"relativeUncertaintyDefined",p.relativeUncertaintyDefined() ? 1.0 : 0.0,"lowerBound",p.lowerBound(),"upperBound",p.upperBound()));
    }
}
