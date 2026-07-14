package com.areina.bulkhead;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.atomic.AtomicReference;

import com.areina.bulkhead.service.AbstractPageService;
import com.areina.bulkhead.service.ConnectionPoolBulkheadService;
import com.areina.bulkhead.service.SchedulerBulkheadService;
import com.areina.bulkhead.service.SemaphoreBulkheadService;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import reactor.core.publisher.Flux;
import reactor.core.publisher.Hooks;
import reactor.core.publisher.Mono;
import reactor.netty.DisposableServer;
import reactor.netty.http.server.HttpServer;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Deterministic isolation test. A stub downstream (Reactor Netty, latency controllable) stands in for
 * availability + recommendations. We fire N <em>truly concurrent</em> page renders per layer and
 * variant (a real load generator in-JVM, unlike spawning curl processes), with recommendations made
 * slow, and count how many <b>critical</b> (availability) responses land within an SLA.
 *
 * <p>The shared resources are shrunk to 2 and the per-downstream ones left ample, so the fragile
 * (shared) variant starves availability while the isolated (bulkhead) variant keeps it healthy.
 */
@SpringBootTest
class BulkheadIsolationTest {

    private static final int N = 100;                 // concurrent renders per run
    private static final Duration SLA = Duration.ofMillis(1000);
    private static final Duration RECS_SLOW = Duration.ofMillis(600);
    private static final Duration AVAIL_FAST = Duration.ofMillis(10);

    private static final String AVAIL_JSON = "{\"eventId\":\"evt\",\"availableSeats\":128,\"price\":59.90}";
    private static final String RECS_JSON = "{\"eventId\":\"evt\",\"relatedEvents\":[\"a\",\"b\",\"c\"]}";

    private static final AtomicReference<Duration> RECS_DELAY = new AtomicReference<>(AVAIL_FAST);
    private static final AtomicReference<Duration> AVAIL_DELAY = new AtomicReference<>(AVAIL_FAST);

    // Started when the class loads, before @DynamicPropertySource reads its port.
    private static final DisposableServer STUB = HttpServer.create().port(0)
            .route(routes -> routes
                    .get("/availability/{id}", (req, res) -> res
                            .header("Content-Type", "application/json")
                            .sendString(Mono.delay(AVAIL_DELAY.get()).thenReturn(AVAIL_JSON)))
                    .get("/recommendations/{id}", (req, res) -> res
                            .header("Content-Type", "application/json")
                            .sendString(Mono.delay(RECS_DELAY.get()).thenReturn(RECS_JSON))))
            .bindNow();

    @DynamicPropertySource
    static void props(DynamicPropertyRegistry r) {
        r.add("resilience.downstream-base-url", () -> "http://localhost:" + STUB.port());
        r.add("resilience.recommendations-timeout-ms", () -> 200);
        // shared = 2 (leaks), per-downstream = ample
        r.add("resilience.scheduler.shared-threads", () -> 2);
        r.add("resilience.scheduler.availability-threads", () -> N);
        r.add("resilience.scheduler.recommendations-threads", () -> N);
        r.add("resilience.pool.shared-max-connections", () -> 2);
        r.add("resilience.pool.per-downstream-max-connections", () -> N);
        r.add("resilience.pool.pending-acquire-timeout-ms", () -> 300);
        r.add("resilience.semaphore.shared-max-concurrent", () -> 2);
        r.add("resilience.semaphore.per-downstream-max-concurrent", () -> N);
        r.add("resilience.semaphore.max-wait-ms", () -> 0);
    }

    // Cancelling a blocking offload leaves the work running; its late result lands on a dead
    // subscriber and Reactor logs onErrorDropped. That is exactly the "blocking can't be cancelled"
    // property, and here just noise, so silence it for a clean run.
    @BeforeAll
    static void quiet() {
        Hooks.onErrorDropped(t -> { });
    }

    @AfterAll
    static void stopStub() {
        Hooks.resetOnErrorDropped();
        STUB.disposeNow();
    }

    @Autowired SchedulerBulkheadService scheduler;
    @Autowired ConnectionPoolBulkheadService pool;
    @Autowired SemaphoreBulkheadService semaphore;

    @Test
    void isolationKeepsTheCriticalPathHealthyUnderADegradedNonCriticalDownstream() {
        RECS_DELAY.set(RECS_SLOW);   // degrade the non-critical downstream

        System.out.println("\n=== Bulkhead isolation (recommendations degraded to " + RECS_SLOW.toMillis()
                + "ms, N=" + N + " concurrent, SLA=" + SLA.toMillis() + "ms) ===");
        System.out.printf("%-10s | %-8s critical OK | %-8s critical OK%n", "layer", "SHARED", "ISOLATED");

        assertLayer("scheduler", scheduler);
        assertLayer("pool", pool);
        assertLayer("semaphore", semaphore);
    }

    private void assertLayer(String name, AbstractPageService service) {
        int fragile = criticalOk(service, false);
        int isolated = criticalOk(service, true);
        System.out.printf("%-10s | %3d/%d           | %3d/%d%n", name, fragile, N, isolated, N);

        // Isolation keeps the critical path essentially healthy...
        assertThat(isolated)
                .as("%s isolated: availability should stay healthy", name)
                .isGreaterThanOrEqualTo((int) (0.85 * N));
        // ...while the shared budget lets the slow non-critical downstream starve it.
        assertThat(fragile)
                .as("%s shared: availability should be starved (worse than isolated)", name)
                .isLessThanOrEqualTo(isolated - N / 5);
    }

    /** Fires N concurrent renders and counts how many produced a page (critical OK) within the SLA. */
    private int criticalOk(AbstractPageService service, boolean isolated) {
        List<Boolean> results = Flux.range(0, N)
                .flatMap(i -> service.render("evt", isolated)
                        .timeout(SLA)
                        .map(v -> Boolean.TRUE)
                        .onErrorReturn(Boolean.FALSE), N)
                .collectList()
                .block(Duration.ofSeconds(30));
        return (int) results.stream().filter(Boolean::booleanValue).count();
    }
}
