package com.areina.bulkhead.service;

import java.time.Duration;

import com.areina.bulkhead.client.DownstreamResponses.AvailabilityResponse;
import com.areina.bulkhead.client.DownstreamResponses.RecommendationsResponse;
import com.areina.bulkhead.config.ResilienceProperties;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.core.publisher.Mono;

/**
 * Layer 2 — shared connection pool. Both downstreams are non-blocking WebClient calls to the same
 * host. Fragile: both go through one small shared {@code ConnectionProvider}, so a slow, hammered
 * recommendations call fills the pool and availability fails to acquire a connection. Isolated: each
 * downstream has its own connection pool.
 */
@Service
public class ConnectionPoolBulkheadService extends AbstractPageService {

    private final WebClient sharedPool;
    private final WebClient availabilityClient;
    private final WebClient recommendationsClient;

    public ConnectionPoolBulkheadService(
            ResilienceProperties props,
            @Qualifier("sharedPoolWebClient") WebClient sharedPool,
            @Qualifier("availabilityWebClient") WebClient availabilityClient,
            @Qualifier("recommendationsWebClient") WebClient recommendationsClient) {
        super(Duration.ofMillis(props.recommendationsTimeoutMs()));
        this.sharedPool = sharedPool;
        this.availabilityClient = availabilityClient;
        this.recommendationsClient = recommendationsClient;
    }

    @Override
    protected Mono<AvailabilityResponse> availability(String eventId, boolean isolated) {
        WebClient client = isolated ? availabilityClient : sharedPool;
        return client.get()
                .uri("/availability/{eventId}", eventId)
                .retrieve()
                .bodyToMono(AvailabilityResponse.class);
    }

    @Override
    protected Mono<RecommendationsResponse> recommendations(String eventId, boolean isolated) {
        WebClient client = isolated ? recommendationsClient : sharedPool;
        return client.get()
                .uri("/recommendations/{eventId}", eventId)
                .retrieve()
                .bodyToMono(RecommendationsResponse.class);
    }
}
