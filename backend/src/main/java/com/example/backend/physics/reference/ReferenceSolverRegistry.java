package com.example.backend.physics.reference;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

@Component
public class ReferenceSolverRegistry {
    private final Map<String, ReferenceSolver> solvers;
    public ReferenceSolverRegistry(List<ReferenceSolver> solvers) {
        this.solvers = solvers.stream().collect(Collectors.toUnmodifiableMap(
                ReferenceSolver::solverId,
                Function.identity(),
                (left, right) -> {
                    throw new IllegalStateException("Duplicate reference solver id: " + left.solverId());
                }));
    }
    public ReferenceSolver get(String solverId) {
        ReferenceSolver solver = solvers.get(solverId);
        if (solver == null) throw new IllegalArgumentException("No independent reference solver registered with id: " + solverId);
        return solver;
    }
    public List<String> ids() { return solvers.keySet().stream().sorted().toList(); }
}
