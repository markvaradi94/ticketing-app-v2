package ro.fasttrackit.gateway;

import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration test (the gateway on a random port, the services replaced by {@link StubBackend}s): a client gets
 * three requests per minute across all routes, then 429. Actuator health is served by the gateway itself and isn't
 * rate-limited.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRateLimitTest {

    private static final StubBackend ticketing = new StubBackend("ticketing");
    private static final StubBackend notifications = new StubBackend("notifications");

    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void routeToTheStubs(DynamicPropertyRegistry registry) {
        registry.add("gateway.ticketing-uri", ticketing::uri);
        registry.add("gateway.notifications-uri", notifications::uri);
        registry.add("gateway.rate-limit.capacity", () -> 3);
        registry.add("gateway.rate-limit.period", () -> "1m");
    }

    @AfterAll
    static void stopBackends() {
        ticketing.stop();
        notifications.stop();
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void aClientOverTheLimitGets429OnEveryRoute() throws Exception {
        assertEquals(200, get("/events").statusCode());
        assertEquals(200, get("/notifications").statusCode());
        assertEquals(200, get("/events").statusCode());

        HttpResponse<String> overTheLimit = get("/notifications");
        assertEquals(429, overTheLimit.statusCode());
        assertEquals("0", overTheLimit.headers().firstValue("X-RateLimit-Remaining").orElseThrow());
        assertEquals(429, get("/events").statusCode());
    }

    @Test
    void healthIsNotRateLimited() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertEquals(200, get("/actuator/health").statusCode());
        }
    }
}
