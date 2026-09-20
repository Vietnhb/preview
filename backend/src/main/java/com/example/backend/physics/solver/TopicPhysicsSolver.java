package com.example.backend.physics.solver;

import java.util.Set;

/** A topic-owned solver that declares its model bindings without central switches. */
public interface TopicPhysicsSolver extends PhysicsSolver {
    Set<String> supportedModels();
}
