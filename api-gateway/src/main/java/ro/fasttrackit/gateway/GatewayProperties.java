package ro.fasttrackit.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

/**
 * Where the services are, and how many requests a client may send per period. Only configuration changes between
 * local runs, Compose and Cloud Run.
 */
@ConfigurationProperties("gateway")
public record GatewayProperties(URI ticketingUri, RateLimit rateLimit) {

    public record RateLimit(long capacity, Duration period) {
    }
}
