package com.example.backend.physics.reference;

import java.util.Set;

/** A topic-owned reference solver that declares its model bindings. */
public interface TopicReferenceSolver extends ReferenceSolver {
    Set<String> supportedModels();
}
