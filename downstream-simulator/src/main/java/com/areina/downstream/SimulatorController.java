package com.areina.downstream;

import java.math.BigDecimal;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ThreadLocalRandom;
import java.util.function.Supplier;

import com.areina.downstream.Knobs.Target;
import com.areina.downstream.Payloads.Availability;
import com.areina.downstream.Payloads.Recommendations;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

@RestController
public class SimulatorController {

    private final Knobs knobs;

    public SimulatorController(Knobs knobs) {
        this.knobs = knobs;
    }

    @GetMapping("/availability/{eventId}")
    public Mono<Availability> availability(@PathVariable String eventId) {
        return respond(Target.AVAILABILITY,
                () -> new Availability(eventId, 128, new BigDecimal("59.90")));
    }

    @GetMapping("/recommendations/{eventId}")
    public Mono<Recommendations> recommendations(@PathVariable String eventId) {
        return respond(Target.RECOMMENDATIONS,
                () -> new Recommendations(eventId, List.of("feid-2026", "rosalia-2026", "karol-g-2027")));
    }

    @PostMapping("/control")
    public Map<String, Knobs.Knob> control(@RequestParam String target,
                                           @RequestParam(required = false) Integer latencyMs,
                                           @RequestParam(required = false) Integer errorRate) {
        knobs.set(Target.valueOf(target.toUpperCase()), latencyMs, errorRate);
        return state();
    }

    @GetMapping("/control")
    public Map<String, Knobs.Knob> state() {
        return Map.of(
                "availability", knobs.snapshot(Target.AVAILABILITY),
                "recommendations", knobs.snapshot(Target.RECOMMENDATIONS));
    }

    /** Answers after the target's injected latency, then rolls its injected error rate. Never blocks. */
    private <T> Mono<T> respond(Target target, Supplier<T> body) {
        int latency = knobs.latencyMs(target);
        int error = knobs.errorRate(target);
        return Mono.delay(Duration.ofMillis(latency))
                .then(Mono.defer(() -> {
                    if (error > 0 && ThreadLocalRandom.current().nextInt(100) < error) {
                        return Mono.error(new ResponseStatusException(
                                HttpStatus.INTERNAL_SERVER_ERROR, "injected error"));
                    }
                    return Mono.fromSupplier(body);
                }));
    }
}
