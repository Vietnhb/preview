package com.example.backend.physics.module;

import com.example.backend.physics.model.CanonicalQuantityBag;
import com.example.backend.exception.PhysicsDomainException;
import com.example.backend.exception.SolverBindingException;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Objects;

/** Immutable registry requiring numerical and reference bindings to resolve as a pair. */
public final class PhysicsModuleRegistry {
    private final Map<String, Registration<?>> byNumericalSolver;
    private final Map<String, Registration<?>> byReferenceSolver;
    private final Map<String, Registration<?>> byModule;

    public PhysicsModuleRegistry(Collection<? extends PhysicsModule<?>> modules) {
        Objects.requireNonNull(modules, "modules");
        Map<String, Registration<?>> numerical = new LinkedHashMap<>();
        Map<String, Registration<?>> reference = new LinkedHashMap<>();
        Map<String, Registration<?>> moduleIds = new LinkedHashMap<>();
        for (PhysicsModule<?> module : modules) {
            if (module == null) throw new IllegalArgumentException("Physics module registration cannot be null");
            requireId(module.moduleId(), "moduleId");
            requireId(module.numericalSolverId(), "numericalSolverId");
            requireId(module.referenceSolverId(), "referenceSolverId");
            Registration<?> registration = new Registration<>(module);
            putUnique(moduleIds, module.moduleId(), registration, "module");
            putUnique(numerical, module.numericalSolverId(), registration, "numerical solver");
            putUnique(reference, module.referenceSolverId(), registration, "reference solver");
        }
        this.byNumericalSolver = Map.copyOf(numerical);
        this.byReferenceSolver = Map.copyOf(reference);
        this.byModule = Map.copyOf(moduleIds);
    }

    public BoundPhysicsModule bind(String numericalSolverId,
                                   String referenceSolverId,
                                   CanonicalQuantityBag quantities) {
        requireId(numericalSolverId, "numericalSolverId");
        requireId(referenceSolverId, "referenceSolverId");
        Objects.requireNonNull(quantities, "quantities");
        Registration<?> numerical = byNumericalSolver.get(numericalSolverId);
        Registration<?> reference = byReferenceSolver.get(referenceSolverId);
        if (numerical == null) {
            throw new SolverBindingException("No physics module registered for numerical solver: " + numericalSolverId);
        }
        if (reference == null) {
            throw new SolverBindingException("No physics module registered for reference solver: " + referenceSolverId);
        }
        if (numerical != reference) {
            throw new SolverBindingException("Numerical/reference solver bindings do not belong to the same module: "
                    + numericalSolverId + " / " + referenceSolverId);
        }
        return numerical.bind(quantities);
    }

    /**
     * Returns false when neither binding is owned by a typed module. If only
     * one side is registered, or the pair belongs to different modules, the
     * catalog binding is inconsistent and must fail instead of falling back.
     */
    public boolean supportsPair(String numericalSolverId, String referenceSolverId) {
        requireId(numericalSolverId, "numericalSolverId");
        requireId(referenceSolverId, "referenceSolverId");
        Registration<?> numerical = byNumericalSolver.get(numericalSolverId);
        Registration<?> reference = byReferenceSolver.get(referenceSolverId);
        if (numerical == null && reference == null) return false;
        if (numerical == null || reference == null || numerical != reference) {
            throw new SolverBindingException("Numerical/reference solver bindings do not belong to the same registered module: "
                    + numericalSolverId + " / " + referenceSolverId);
        }
        return true;
    }

    public BoundPhysicsModule bindModule(String moduleId, CanonicalQuantityBag quantities) {
        requireId(moduleId, "moduleId");
        Objects.requireNonNull(quantities, "quantities");
        Registration<?> registration = byModule.get(moduleId);
        if (registration == null) throw new SolverBindingException("No physics module registered: " + moduleId);
        return registration.bind(quantities);
    }

    public int size() {
        return byModule.size();
    }

    private static void requireId(String id, String field) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException(field + " is required");
    }

    private static void putUnique(Map<String, Registration<?>> registrations,
                                  String id,
                                  Registration<?> registration,
                                  String kind) {
        if (registrations.putIfAbsent(id, registration) != null) {
            throw new IllegalStateException("Duplicate " + kind + " binding: " + id);
        }
    }

    private record Registration<P>(PhysicsModule<P> module) {
        private Registration {
            Objects.requireNonNull(module, "module");
        }

        private BoundPhysicsModule bind(CanonicalQuantityBag quantities) {
            final P parameters;
            try {
                parameters = Objects.requireNonNull(module.bind(quantities), "module parameters");
            } catch (PhysicsDomainException failure) {
                throw failure;
            } catch (IllegalArgumentException failure) {
                throw new PhysicsDomainException("Physics module " + module.moduleId()
                        + " rejected quantities outside its physical domain: " + failure.getMessage(), failure);
            }
            return BoundPhysicsModule.bind(module, parameters);
        }
    }
}
