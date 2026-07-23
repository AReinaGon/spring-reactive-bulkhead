package com.areina.bulkhead.model;

import java.math.BigDecimal;
import java.util.List;

/**
 * The aggregated event page returned by the BFF.
 *
 * <p>{@code availableSeats}/{@code price} come from the critical downstream and must never depend on
 * recommendations answering. When the non-critical downstream is degraded, {@code relatedEvents} is
 * empty and {@code recommendationsDegraded} is {@code true}, yet the page is still a success.
 */
public record EventPageView(
        String eventId,
        int availableSeats,
        BigDecimal price,
        List<String> relatedEvents,
        boolean recommendationsDegraded,
        String handledBy) {
}
