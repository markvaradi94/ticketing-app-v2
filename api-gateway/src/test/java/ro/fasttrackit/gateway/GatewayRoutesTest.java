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
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test (the gateway on a random port, the services replaced by {@link StubBackend}s): each path goes to
 * its service, and every request carries a correlation id, the client's or a new one, to the service and back. The
 * rate limit is high here; {@link GatewayRateLimitTest} tests it.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutesTest {

    private static final StubBackend ticketing = new StubBackend("ticketing");
    private static final StubBackend notifications = new StubBackend("notifications");

    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void routeToTheStubs(DynamicPropertyRegistry registry) {
        registry.add("gateway.ticketing-uri", ticketing::uri);
        registry.add("gateway.notifications-uri", notifications::uri);
        registry.add("gateway.rate-limit.capacity", () -> 1000);
    }

    @AfterAll
    static void stopBackends() {
        ticketing.stop();
        notifications.stop();
    }

    private HttpResponse<String> get(String path, String... headers) throws Exception {
        HttpRequest.Builder request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path));
        if (headers.length > 0) {
            request.headers(headers);
        }
        return client.send(request.build(), HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void eventsGoToTicketing() throws Exception {
        HttpResponse<String> response = get("/events/rock-cluj", CorrelationIdFilter.HEADER, "id-1");

        assertEquals(200, response.statusCode());
        assertEquals("ticketing GET /events/rock-cluj id-1", response.body());
    }

    @Test
    void notificationsGoToNotificationService() throws Exception {
        HttpResponse<String> response = get("/notifications", CorrelationIdFilter.HEADER, "id-2");

        assertEquals(200, response.statusCode());
        assertEquals("notifications GET /notifications id-2", response.body());
    }

    @Test
    void aClientsCorrelationIdIsKeptAndReturned() throws Exception {
        HttpResponse<String> response = get("/events", CorrelationIdFilter.HEADER, "client-chosen-id");

        assertTrue(response.body().endsWith(" client-chosen-id"), response.body());
        assertEquals("client-chosen-id", response.headers().firstValue(CorrelationIdFilter.HEADER).orElseThrow());
    }

    @Test
    void aRequestWithoutCorrelationIdGetsANewOneThatReachesTheService() throws Exception {
        HttpResponse<String> response = get("/events");

        String returned = response.headers().firstValue(CorrelationIdFilter.HEADER).orElseThrow();
        assertEquals(36, returned.length(), "expected a UUID, got " + returned);
        assertEquals("ticketing GET /events " + returned, response.body());
    }
}
