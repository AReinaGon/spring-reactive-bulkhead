package com.areina.bulkhead.service;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

import com.areina.bulkhead.client.DownstreamResponses.AvailabilityResponse;
import com.areina.bulkhead.client.DownstreamResponses.RecommendationsResponse;
import com.areina.bulkhead.model.EventPageView;

import reactor.core.publisher.Mono;

/**
 * Composes the event page out of the two downstreams. The composition and the fallback policy are
 * identical across the three layers; only <em>how</em> each downstream is called (the isolation
 * mechanism) differs, which is what the subclasses provide.
 *
 * <p>Policy: the critical call (availability) is never masked — if it fails or is starved, the page
 * fails, and that is what the experiment measures. The non-critical call (recommendations) falls back
 * to an empty sidebar with {@code recommendationsDegraded=true}.
 */
public abstract class AbstractPageService {

    private final String instanceId = "bulkhead-" + UUID.randomUUID().toString().substring(0, 8);

    private final Duration recommendationsTimeout;

    protected AbstractPageService(Duration recommendationsTimeout) {
        this.recommendationsTimeout = recommendationsTimeout;
    }

    protected abstract Mono<AvailabilityResponse> availability(String eventId, boolean isolated);

    protected abstract Mono<RecommendationsResponse> recommendations(String eventId, boolean isolated);

    public Mono<EventPageView> render(String eventId, boolean isolated) {
        // Critical: never masked. If availability is starved or fails, the page fails — that is the
        // measured symptom of a leaking shared resource.
        Mono<AvailabilityResponse> critical = availability(eventId, isolated);

        // Non-critical: a short timeout keeps the page from ever blocking on a slow sidebar, and any
        // error/timeout/rejection falls back to an empty sidebar. This is the "composition" half; the
        // per-downstream bulkhead is the "isolation" half. Both are needed: the timeout alone does not
        // stop recommendations from exhausting the shared resource that availability also needs.
        Mono<RecsOutcome> sidebar = recommendations(eventId, isolated)
                .map(r -> new RecsOutcome(r.relatedEvents(), false))
                .timeout(recommendationsTimeout)
                .onErrorReturn(new RecsOutcome(List.of(), true));

        return Mono.zip(critical, sidebar)
                .map(t -> new EventPageView(
                        eventId,
                        t.getT1().availableSeats(),
                        t.getT1().price(),
                        t.getT2().related(),
                        t.getT2().degraded(),
                        instanceId));
    }

    protected record RecsOutcome(List<String> related, boolean degraded) {}
}
