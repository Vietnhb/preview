package com.example.backend.physics.compatibility;

import com.example.backend.physics.model.SolverOutput;
import com.example.backend.physics.output.PhysicsOutputFrame;
import java.util.Map;
import com.example.backend.physics.output.PhysicsOutputContract;
import com.example.backend.physics.output.PhysicsOutputFrameMapper;

/** Versioned bridge from existing solver transport to the typed output domain. */
public final class LegacySolverOutputAdapter {
    private LegacySolverOutputAdapter() { }

    public static PhysicsOutputFrame adapt(SolverOutput output, Map<String, String> declaredUnits) {
        return PhysicsOutputFrameMapper.fromSolverOutput(output, declaredUnits);
    }

    public static PhysicsOutputFrame adapt(SolverOutput output, PhysicsOutputContract contract) {
        return PhysicsOutputFrameMapper.fromSolverOutput(output, contract);
    }
}
