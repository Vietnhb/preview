package com.example.backend.physics.validation;

import java.util.List;
import java.util.Objects;

/** Immutable, compiled end-condition payload. No JsonNode crosses this boundary. */
public sealed interface EndConditionContract permits EndConditionContract.TimeLimit,
        EndConditionContract.Threshold, EndConditionContract.Event, EndConditionContract.CycleCount,
        EndConditionContract.Manual {
    EndConditionType type();

    Double maxTime();

    record TimeLimit(double duration) implements EndConditionContract {
        public TimeLimit {
            if (!Double.isFinite(duration) || duration <= 0) throw new IllegalArgumentException("duration must be positive and finite");
        }
        @Override public EndConditionType type() { return EndConditionType.TIME_LIMIT; }
        @Override public Double maxTime() { return duration; }
    }

    record Threshold(OutputSourceBinding source, ComparisonOperator operator, double value,
            Double maxTime) implements EndConditionContract {
        public Threshold {
            Objects.requireNonNull(source, "source");
            Objects.requireNonNull(operator, "operator");
            if (!Double.isFinite(value)) throw new IllegalArgumentException("threshold value must be finite");
            requireOptionalMaxTime(maxTime);
        }
        @Override public EndConditionType type() { return EndConditionType.THRESHOLD; }
    }

    enum EventKind { CONTACT, COLLISION }

    record Event(EventKind kind, List<String> entities, OutputSourceBinding source,
            ComparisonOperator operator, Double value, OutputSourceBinding firstSource,
            OutputSourceBinding secondSource, OutputSourceBinding markerSource,
            Double maxTime) implements EndConditionContract {
        public Event {
            Objects.requireNonNull(kind, "kind");
            entities = entities == null ? List.of() : List.copyOf(entities);
            if (entities.isEmpty() || entities.stream().anyMatch(entity -> entity == null || entity.isBlank()))
                throw new IllegalArgumentException("event requires entity identifiers");
            if (kind == EventKind.CONTACT) {
                Objects.requireNonNull(source, "contact source");
                Objects.requireNonNull(operator, "contact operator");
                if (value == null || !Double.isFinite(value)) throw new IllegalArgumentException("contact value must be finite");
            }
            if ((firstSource == null) != (secondSource == null))
                throw new IllegalArgumentException("Collision source bindings must be provided as a pair");
            if (kind == EventKind.COLLISION && firstSource == null && markerSource == null)
                throw new IllegalArgumentException("Collision requires typed source bindings or a declared marker");
            requireOptionalMaxTime(maxTime);
        }
        @Override public EndConditionType type() { return EndConditionType.EVENT; }
    }

    record CycleCount(OutputSourceBinding source, int count, Double maxTime) implements EndConditionContract {
        public CycleCount {
            Objects.requireNonNull(source, "source");
            if (count <= 0) throw new IllegalArgumentException("cycle count must be positive");
            requireOptionalMaxTime(maxTime);
        }
        @Override public EndConditionType type() { return EndConditionType.CYCLE_COUNT; }
    }

    record Manual(Double maxTime) implements EndConditionContract {
        public Manual { requireOptionalMaxTime(maxTime); }
        @Override public EndConditionType type() { return EndConditionType.MANUAL; }
    }

    private static void requireOptionalMaxTime(Double maxTime) {
        if (maxTime != null && (!Double.isFinite(maxTime) || maxTime <= 0
                || maxTime > EndConditionResolver.MAX_DYNAMIC_SECONDS)) {
            throw new IllegalArgumentException("maxTime must be finite, positive and within the dynamic limit");
        }
    }
}
