package com.areina.bulkhead.config;

import java.time.Duration;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.bulkhead.BulkheadConfig;
import io.github.resilience4j.bulkhead.BulkheadRegistry;
import io.github.resilience4j.micrometer.tagged.TaggedBulkheadMetrics;
import io.micrometer.core.instrument.MeterRegistry;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Layer 3 (concurrency limit). Resilience4j semaphore bulkheads, bound to Micrometer so Actuator
 * exposes {@code resilience4j_bulkhead_available_concurrent_calls}. The fragile variant funnels both
 * downstreams through {@code shared}; the isolated variant uses one bulkhead per downstream.
 */
@Configuration
public class Resilience4jConfig {

    @Bean
    public BulkheadRegistry bulkheadRegistry(ResilienceProperties props, MeterRegistry meterRegistry) {
        BulkheadRegistry registry = BulkheadRegistry.ofDefaults();

        BulkheadConfig shared = BulkheadConfig.custom()
                .maxConcurrentCalls(props.semaphore().sharedMaxConcurrent())
                .maxWaitDuration(Duration.ofMillis(props.semaphore().maxWaitMs()))
                .build();
        BulkheadConfig perDownstream = BulkheadConfig.custom()
                .maxConcurrentCalls(props.semaphore().perDownstreamMaxConcurrent())
                .maxWaitDuration(Duration.ofMillis(props.semaphore().maxWaitMs()))
                .build();

        registry.bulkhead("shared", shared);
        registry.bulkhead("availability", perDownstream);
        registry.bulkhead("recommendations", perDownstream);

        TaggedBulkheadMetrics.ofBulkheadRegistry(registry).bindTo(meterRegistry);
        return registry;
    }

    @Bean
    public Bulkhead sharedBulkhead(BulkheadRegistry registry) {
        return registry.bulkhead("shared");
    }

    @Bean
    public Bulkhead availabilityBulkhead(BulkheadRegistry registry) {
        return registry.bulkhead("availability");
    }

    @Bean
    public Bulkhead recommendationsBulkhead(BulkheadRegistry registry) {
        return registry.bulkhead("recommendations");
    }
}
