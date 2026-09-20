package com.example.backend.physics.validation;

import com.example.backend.physics.model.SolverOutput;

import java.util.EnumMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/** Immutable strategy map with startup-time duplicate and missing-type checks. */
public final class EndConditionStrategyRegistry {
    private static final Map<EndConditionType, Class<? extends EndConditionContract>> CONTRACT_TYPES = Map.of(
            EndConditionType.TIME_LIMIT, EndConditionContract.TimeLimit.class,
            EndConditionType.THRESHOLD, EndConditionContract.Threshold.class,
            EndConditionType.EVENT, EndConditionContract.Event.class,
            EndConditionType.CYCLE_COUNT, EndConditionContract.CycleCount.class,
            EndConditionType.MANUAL, EndConditionContract.Manual.class);
    private final Map<EndConditionType, EndConditionStrategy<?>> strategies;

    public EndConditionStrategyRegistry(List<EndConditionStrategy<?>> strategies) {
        EnumMap<EndConditionType, EndConditionStrategy<?>> copy = new EnumMap<>(EndConditionType.class);
        for (EndConditionStrategy<?> strategy : strategies) {
            Objects.requireNonNull(strategy, "strategy");
            if (CONTRACT_TYPES.get(strategy.type()) != strategy.contractType())
                throw new IllegalArgumentException("End-condition strategy has wrong contract class: " + strategy.type());
            if (copy.putIfAbsent(strategy.type(), strategy) != null)
                throw new IllegalArgumentException("Duplicate end-condition strategy: " + strategy.type());
        }
        for (EndConditionType type : EndConditionType.values())
            if (!copy.containsKey(type)) throw new IllegalArgumentException("Missing end-condition strategy: " + type);
        this.strategies = Map.copyOf(copy);
    }

    public EndConditionResolver.ResolvedEnd resolve(EndConditionContract condition, SolverOutput output, double limit) {
        Objects.requireNonNull(condition, "condition");
        EndConditionStrategy<?> strategy = strategies.get(condition.type());
        if (strategy == null) throw new IllegalStateException("No strategy registered for " + condition.type());
        if (!strategy.contractType().isInstance(condition))
            throw new IllegalArgumentException("Wrong end-condition contract for " + condition.type());
        return strategy.dispatch(condition, output, limit);
    }

    public int size() { return strategies.size(); }
}
