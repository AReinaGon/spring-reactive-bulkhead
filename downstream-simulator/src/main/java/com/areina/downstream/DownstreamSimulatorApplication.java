package com.areina.downstream;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Fake "availability" and "recommendations" downstreams for the bulkhead PoC.
 *
 * <p>It is deliberately dumb: it just answers each endpoint after an injectable latency (and,
 * optionally, with an injectable error rate). The three bulkhead dimensions (shared scheduler,
 * shared connection pool, shared concurrency limit) all live on the caller side (the BFF); from
 * here a slow downstream is simply an endpoint told to answer slowly.
 */
@SpringBootApplication
public class DownstreamSimulatorApplication {
    public static void main(String[] args) {
        SpringApplication.run(DownstreamSimulatorApplication.class, args);
    }
}
