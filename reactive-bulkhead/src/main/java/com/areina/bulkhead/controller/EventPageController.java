package com.areina.bulkhead.controller;

import com.areina.bulkhead.model.EventPageView;
import com.areina.bulkhead.service.ConnectionPoolBulkheadService;
import com.areina.bulkhead.service.SchedulerBulkheadService;
import com.areina.bulkhead.service.SemaphoreBulkheadService;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import reactor.core.publisher.Mono;

/**
 * Renders the event page. {@code mode} selects which shared resource to stress (scheduler / pool /
 * semaphore) and {@code isolated} toggles the fragile (shared) vs bulkhead (per-downstream) variant.
 */
@RestController
@RequestMapping("/api/events")
public class EventPageController {

    private final SchedulerBulkheadService scheduler;
    private final ConnectionPoolBulkheadService pool;
    private final SemaphoreBulkheadService semaphore;

    public EventPageController(SchedulerBulkheadService scheduler,
                               ConnectionPoolBulkheadService pool,
                               SemaphoreBulkheadService semaphore) {
        this.scheduler = scheduler;
        this.pool = pool;
        this.semaphore = semaphore;
    }

    @GetMapping("/{eventId}/page")
    public Mono<EventPageView> page(@PathVariable String eventId,
                                    @RequestParam(defaultValue = "semaphore") String mode,
                                    @RequestParam(defaultValue = "false") boolean isolated) {
        return switch (mode) {
            case "scheduler" -> scheduler.render(eventId, isolated);
            case "pool" -> pool.render(eventId, isolated);
            case "semaphore" -> semaphore.render(eventId, isolated);
            default -> Mono.error(new ResponseStatusException(
                    HttpStatus.BAD_REQUEST, "unknown mode '" + mode + "' (use scheduler|pool|semaphore)"));
        };
    }
}
