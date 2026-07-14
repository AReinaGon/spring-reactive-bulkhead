package com.areina.downstream;

import java.math.BigDecimal;
import java.util.List;

/** Response bodies of the two fake downstreams. */
public final class Payloads {

    /** Critical downstream: seats + price for an event. */
    public record Availability(String eventId, int availableSeats, BigDecimal price) {}

    /** Non-critical downstream: the "related events" sidebar. */
    public record Recommendations(String eventId, List<String> relatedEvents) {}

    private Payloads() {}
}
