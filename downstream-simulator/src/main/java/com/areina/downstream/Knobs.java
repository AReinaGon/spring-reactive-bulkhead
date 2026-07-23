package com.areina.downstream;

import java.util.Map;
import java.util.concurrent.atomic.AtomicInteger;

import org.springframework.stereotype.Component;

/**
 * In-memory, thread-safe latency/error knobs per downstream, tunable at runtime via the control
 * endpoints so a load test can degrade "recommendations" mid-run without a restart.
 */
@Component
public class Knobs {

    public enum Target { AVAILABILITY, RECOMMENDATIONS }

    public record Knob(int latencyMs, int errorRate) {}

    private final Map<Target, AtomicInteger> latencyMs = Map.of(
            Target.AVAILABILITY, new AtomicInteger(20),
            Target.RECOMMENDATIONS, new AtomicInteger(20));

    private final Map<Target, AtomicInteger> errorRate = Map.of(
            Target.AVAILABILITY, new AtomicInteger(0),
            Target.RECOMMENDATIONS, new AtomicInteger(0));

    public int latencyMs(Target t) {
        return latencyMs.get(t).get();
    }

    public int errorRate(Target t) {
        return errorRate.get(t).get();
    }

    public void set(Target t, Integer latencyMs, Integer errorRate) {
        if (latencyMs != null) {
            this.latencyMs.get(t).set(Math.max(0, latencyMs));
        }
        if (errorRate != null) {
            this.errorRate.get(t).set(Math.min(100, Math.max(0, errorRate)));
        }
    }

    public Knob snapshot(Target t) {
        return new Knob(latencyMs(t), errorRate(t));
    }
}
