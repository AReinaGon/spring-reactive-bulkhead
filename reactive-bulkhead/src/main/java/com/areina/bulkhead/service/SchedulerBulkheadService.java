package com.areina.bulkhead.service;

import java.time.Duration;

import com.areina.bulkhead.client.DownstreamResponses.AvailabilityResponse;
import com.areina.bulkhead.client.DownstreamResponses.RecommendationsResponse;
import com.areina.bulkhead.config.ResilienceProperties;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import reactor.core.publisher.Mono;
import reactor.core.scheduler.Scheduler;

/**
 * Layer 1 — shared blocking scheduler. Both downstreams are consumed with a <em>blocking</em> client
 * (a legacy-SDK stand-in) offloaded with {@code subscribeOn}. Fragile: both run on the shared
 * scheduler, so a slow recommendations call exhausts its threads and availability queues behind it.
 * Isolated: each downstream gets its own scheduler.
 */
@Service
public class SchedulerBulkheadService extends AbstractPageService {

    private final RestClient restClient;
    private final Scheduler sharedScheduler;
    private final Scheduler availabilityScheduler;
    private final Scheduler recommendationsScheduler;

    public SchedulerBulkheadService(
            ResilienceProperties props,
            @Qualifier("sharedBlockingScheduler") Scheduler sharedScheduler,
            @Qualifier("availabilityScheduler") Scheduler availabilityScheduler,
            @Qualifier("recommendationsScheduler") Scheduler recommendationsScheduler) {
        super(Duration.ofMillis(props.recommendationsTimeoutMs()));
        this.restClient = RestClient.create(props.downstreamBaseUrl());
        this.sharedScheduler = sharedScheduler;
        this.availabilityScheduler = availabilityScheduler;
        this.recommendationsScheduler = recommendationsScheduler;
    }

    @Override
    protected Mono<AvailabilityResponse> availability(String eventId, boolean isolated) {
        Scheduler scheduler = isolated ? availabilityScheduler : sharedScheduler;
        return Mono.fromCallable(() -> restClient.get()
                        .uri("/availability/{eventId}", eventId)
                        .retrieve()
                        .body(AvailabilityResponse.class))
                .subscribeOn(scheduler);
    }

    @Override
    protected Mono<RecommendationsResponse> recommendations(String eventId, boolean isolated) {
        Scheduler scheduler = isolated ? recommendationsScheduler : sharedScheduler;
        return Mono.fromCallable(() -> restClient.get()
                        .uri("/recommendations/{eventId}", eventId)
                        .retrieve()
                        .body(RecommendationsResponse.class))
                .subscribeOn(scheduler);
    }
}
