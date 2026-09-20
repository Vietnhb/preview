package com.example.backend.physics.validation;

import com.example.backend.physics.model.SolverOutput;

/** A strategy handles one compiled condition type. */
public interface EndConditionStrategy<T extends EndConditionContract> {
    EndConditionType type();
    Class<T> contractType();
    EndConditionResolver.ResolvedEnd resolve(T condition, SolverOutput output, double limit);

    default EndConditionResolver.ResolvedEnd dispatch(EndConditionContract condition, SolverOutput output,
            double limit) {
        return resolve(contractType().cast(condition), output, limit);
    }
}
