package ro.fasttrackit.gateway;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.net.URI;
import java.time.Duration;

/**
 * Where the services are, how many requests a client may send per period, and whether the services need an
 * identity token (on Cloud Run). Only configuration changes between local runs, Compose and Cloud Run.
 */
@ConfigurationProperties("gateway")
public record GatewayProperties(URI ticketingUri, URI notificationsUri, RateLimit rateLimit, CloudRunAuth cloudRunAuth) {

    public record RateLimit(long capacity, Duration period) {
    }

    /** On Cloud Run the services are private: the gateway gets an ID token from the metadata server for each one. */
    public record CloudRunAuth(boolean enabled, URI metadataUri) {
    }
}
