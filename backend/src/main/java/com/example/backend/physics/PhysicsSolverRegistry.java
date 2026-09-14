package com.example.backend.physics;

import org.springframework.stereotype.Component;

import java.util.List;

@Component
public class PhysicsSolverRegistry {
    private final List<PhysicsSolver> solvers;

    public PhysicsSolverRegistry(List<PhysicsSolver> solvers) {
        this.solvers = solvers;
    }

    public PhysicsSolver get(String solverId) {
        return solvers.stream()
                .filter(solver -> solver.solverId().equals(solverId))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("No numerical solver registered with id: " + solverId));
    }
    public List<String> ids() { return solvers.stream().map(PhysicsSolver::solverId).sorted().toList(); }
}
