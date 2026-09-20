package com.example.backend.physics.solver;

import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
public class PhysicsSolverRegistry {
    private final Map<String, PhysicsSolver> solvers;

    public PhysicsSolverRegistry(List<PhysicsSolver> solvers) {
        this.solvers = solvers.stream().collect(Collectors.toUnmodifiableMap(
                PhysicsSolver::solverId,
                Function.identity(),
                (left, right) -> {
                    throw new IllegalStateException("Duplicate numerical solver id: " + left.solverId());
                }));
    }

    public PhysicsSolver get(String solverId) {
        PhysicsSolver solver = solvers.get(solverId);
        if (solver == null) throw new IllegalArgumentException("No numerical solver registered with id: " + solverId);
        return solver;
    }
    public List<String> ids() { return solvers.keySet().stream().sorted().toList(); }
}
