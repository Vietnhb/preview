package com.example.backend.physics.runtime;

import java.util.ArrayList;
import java.util.List;

/** Shared bounded sampling grid for solvers whose outputs are static in time. */
public final class SimulationTimeline {
    private static final int MAX_SAMPLES = 16_384;

    private SimulationTimeline() { }

    public static List<Double> sample(double duration, double step) {
        if (!Double.isFinite(duration) || duration <= 0 || !Double.isFinite(step) || step <= 0) {
            throw new IllegalArgumentException("durationSeconds and stepSeconds must be finite and positive");
        }
        if (Math.ceil(duration / step) > MAX_SAMPLES)
            throw new IllegalArgumentException("Requested sampling horizon exceeds the sample limit; duration must not be silently truncated");
        int count = Math.max(1, (int) Math.ceil(duration / step));
        List<Double> time = new ArrayList<>(count + 1);
        for (int i = 0; i <= count; i++) time.add(Math.min(duration, i * step));
        return List.copyOf(time);
    }
}
