package com.example.backend.physics;

import java.util.List;
import org.springframework.stereotype.Component;

@Component
public class ReferenceSolverRegistry {
    private final List<ReferenceSolver> solvers;
    public ReferenceSolverRegistry(List<ReferenceSolver> solvers) { this.solvers = List.copyOf(solvers); }
    public ReferenceSolver get(String solverId) {
        return solvers.stream().filter(solver -> solver.solverId().equals(solverId)).findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No independent reference solver registered with id: " + solverId));
    }
    public List<String> ids() { return solvers.stream().map(ReferenceSolver::solverId).sorted().toList(); }
}
