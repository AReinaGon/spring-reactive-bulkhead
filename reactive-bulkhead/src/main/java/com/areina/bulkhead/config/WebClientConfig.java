package com.areina.bulkhead.config;

import java.time.Duration;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.reactive.ReactorClientHttpConnector;
import org.springframework.web.reactive.function.client.WebClient;

import reactor.netty.http.client.HttpClient;
import reactor.netty.resources.ConnectionProvider;

/**
 * Layer 2 (connection pool) and the transport for layer 3.
 *
 * <p>Reactor Netty pools connections <em>per remote host</em>, so two downstreams on different hosts
 * are already isolated for free. The leak appears when they share one host (a common API gateway).
 * The fragile variant routes both through {@link #sharedPoolWebClient} (one small pool); the isolated
 * variant gives each downstream its own {@link ConnectionProvider}. The ample-pool client is used by
 * the semaphore layer so the pool is never the bottleneck there.
 */
@Configuration
public class WebClientConfig {

    private static WebClient client(String baseUrl, ConnectionProvider provider) {
        return WebClient.builder()
                .baseUrl(baseUrl)
                .clientConnector(new ReactorClientHttpConnector(HttpClient.create(provider)))
                .build();
    }

    @Bean
    public WebClient sharedPoolWebClient(ResilienceProperties props) {
        ConnectionProvider provider = ConnectionProvider.builder("shared-pool")
                .maxConnections(props.pool().sharedMaxConnections())
                .pendingAcquireTimeout(Duration.ofMillis(props.pool().pendingAcquireTimeoutMs()))
                .build();
        return client(props.downstreamBaseUrl(), provider);
    }

    @Bean
    public WebClient availabilityWebClient(ResilienceProperties props) {
        ConnectionProvider provider = ConnectionProvider.builder("availability-pool")
                .maxConnections(props.pool().perDownstreamMaxConnections())
                .pendingAcquireTimeout(Duration.ofMillis(props.pool().pendingAcquireTimeoutMs()))
                .build();
        return client(props.downstreamBaseUrl(), provider);
    }

    @Bean
    public WebClient recommendationsWebClient(ResilienceProperties props) {
        ConnectionProvider provider = ConnectionProvider.builder("recommendations-pool")
                .maxConnections(props.pool().perDownstreamMaxConnections())
                .pendingAcquireTimeout(Duration.ofMillis(props.pool().pendingAcquireTimeoutMs()))
                .build();
        return client(props.downstreamBaseUrl(), provider);
    }

    @Bean
    public WebClient amplePoolWebClient(ResilienceProperties props) {
        ConnectionProvider provider = ConnectionProvider.builder("ample-pool")
                .maxConnections(1000)
                .pendingAcquireTimeout(Duration.ofSeconds(10))
                .build();
        return client(props.downstreamBaseUrl(), provider);
    }
}
