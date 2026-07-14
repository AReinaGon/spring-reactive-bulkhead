package com.areina.bulkhead.client;

import java.math.BigDecimal;
import java.util.List;

/** Deserialization targets for the two downstream responses. */
public final class DownstreamResponses {

    public record AvailabilityResponse(String eventId, int availableSeats, BigDecimal price) {}

    public record RecommendationsResponse(String eventId, List<String> relatedEvents) {}

    private DownstreamResponses() {}
}
