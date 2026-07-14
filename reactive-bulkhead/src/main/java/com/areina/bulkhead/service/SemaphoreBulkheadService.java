package com.areina.bulkhead.service;

import java.time.Duration;

import com.areina.bulkhead.client.DownstreamResponses.AvailabilityResponse;
import com.areina.bulkhead.client.DownstreamResponses.RecommendationsResponse;
import com.areina.bulkhead.config.ResilienceProperties;

import io.github.resilience4j.bulkhead.Bulkhead;
import io.github.resilience4j.reactor.bulkhead.operator.BulkheadOperator;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

/**
 * Layer 3 — shared concurrency limit. Both downstreams are non-blocking WebClient calls over an
 * ample connection pool (so the pool is not the bottleneck), each guarded by a Resilience4j semaphore
 * bulkhead. Fragile: both share one bulkhead, so recommendations consumes the in-flight budget and
 * availability is rejected. Isolated: one bulkhead per downstream.
 */
@Service
public class SemaphoreBulkheadService extends AbstractPageService {

    private final WebClient webClient;
    private final Bulkhead sharedBulkhead;
    private final Bulkhead availabilityBulkhead;
    private final Bulkhead recommendationsBulkhead;

    public SemaphoreBulkheadService(
            ResilienceProperties props,
            @Qualifier("amplePoolWebClient") WebClient webClient,
            @Qualifier("sharedBulkhead") Bulkhead sharedBulkhead,
            @Qualifier("availabilityBulkhead") Bulkhead availabilityBulkhead,
            @Qualifier("recommendationsBulkhead") Bulkhead recommendationsBulkhead) {
        super(Duration.ofMillis(props.recommendationsTimeoutMs()));
        this.webClient = webClient;
        this.sharedBulkhead = sharedBulkhead;
        this.availabilityBulkhead = availabilityBulkhead;
        this.recommendationsBulkhead = recommendationsBulkhead;
    }

    @Override
    protected Mono<AvailabilityResponse> availability(String eventId, boolean isolated) {
        Bulkhead bulkhead = isolated ? availabilityBulkhead : sharedBulkhead;
        return webClient.get()
                .uri("/availability/{eventId}", eventId)
                .retrieve()
                .bodyToMono(AvailabilityResponse.class)
                .transformDeferred(BulkheadOperator.of(bulkhead));
    }

    @Override
    protected Mono<RecommendationsResponse> recommendations(String eventId, boolean isolated) {
        Bulkhead bulkhead = isolated ? recommendationsBulkhead : sharedBulkhead;
        return webClient.get()
                .uri("/recommendations/{eventId}", eventId)
                .retrieve()
                .bodyToMono(RecommendationsResponse.class)
                .transformDeferred(BulkheadOperator.of(bulkhead));
    }
}
