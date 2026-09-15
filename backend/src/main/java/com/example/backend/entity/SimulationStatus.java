package com.example.backend.entity;

public enum SimulationStatus {
    VALIDATING,
    READY,
    BLOCKED,
    FAILED,
    /** Kept for simulations created by the previous preview schema. */
    ARCHIVED
}
