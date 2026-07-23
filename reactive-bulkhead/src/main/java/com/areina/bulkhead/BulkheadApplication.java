package com.areina.bulkhead;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * Event-page BFF that aggregates a critical downstream (availability) and a non-critical one
 * (recommendations). It demonstrates the Bulkhead pattern across three shared resources that can
 * still leak in a reactive stack: a shared blocking scheduler, a shared connection pool, and a
 * shared concurrency limit.
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class BulkheadApplication {
    public static void main(String[] args) {
        SpringApplication.run(BulkheadApplication.class, args);
    }
}
