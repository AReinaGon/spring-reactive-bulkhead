package com.areina.bulkhead.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import reactor.core.scheduler.Scheduler;
import reactor.core.scheduler.Schedulers;

/**
 * Layer 1 (blocking offload). The fragile variant runs both downstreams' blocking calls on the
 * {@link #sharedBlockingScheduler}; a slow one exhausts its threads and starves the other. The
 * isolated variant gives each downstream a dedicated scheduler — the "thread pool" flavour of the
 * bulkhead, which for blocking offload is the correct one (unlike non-blocking calls, where a
 * semaphore is preferred).
 */
@Configuration
public class SchedulerConfig {

    @Bean(destroyMethod = "dispose")
    public Scheduler sharedBlockingScheduler(ResilienceProperties props) {
        return Schedulers.newBoundedElastic(props.scheduler().sharedThreads(), 100_000, "shared-blocking");
    }

    @Bean(destroyMethod = "dispose")
    public Scheduler availabilityScheduler(ResilienceProperties props) {
        return Schedulers.newBoundedElastic(props.scheduler().availabilityThreads(), 100_000, "availability-blocking");
    }

    @Bean(destroyMethod = "dispose")
    public Scheduler recommendationsScheduler(ResilienceProperties props) {
        return Schedulers.newBoundedElastic(props.scheduler().recommendationsThreads(), 100_000, "recommendations-blocking");
    }
}
