package com.areina.bulkhead;

import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;

/**
 * Sanity check: the full context wires (all three services, their qualified schedulers / connection
 * pools / bulkheads, the properties binding and the Micrometer metrics registration) and boots. No
 * network is touched at startup, so this needs no Docker.
 */
@SpringBootTest
class ContextLoadsTest {

    @Test
    void contextLoads() {
    }
}
