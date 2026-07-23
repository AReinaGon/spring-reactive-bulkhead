package com.areina.bulkhead.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Externalised knobs for the three bulkhead dimensions plus the downstream base URL. All values are
 * overridable per environment so a load test can shrink a shared resource to make it leak on demand.
 */
@ConfigurationProperties(prefix = "resilience")
public record ResilienceProperties(
        String downstreamBaseUrl,
        int recommendationsTimeoutMs,
        Scheduler scheduler,
        Pool pool,
        Semaphore semaphore) {

    /** Layer 1: bounded schedulers for the blocking offload. */
    public record Scheduler(int sharedThreads, int availabilityThreads, int recommendationsThreads) {}

    /** Layer 2: Reactor Netty connection pools. */
    public record Pool(int sharedMaxConnections, int perDownstreamMaxConnections, int pendingAcquireTimeoutMs) {}

    /** Layer 3: Resilience4j semaphore bulkheads. */
    public record Semaphore(int sharedMaxConcurrent, int perDownstreamMaxConcurrent, int maxWaitMs) {}
}
