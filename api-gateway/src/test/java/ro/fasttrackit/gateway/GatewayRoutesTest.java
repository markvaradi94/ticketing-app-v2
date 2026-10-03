package ro.fasttrackit.gateway;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration test (the gateway on a random port, the backend a tiny JDK HTTP server): requests to
 * {@code /events/**} reach the backend until the client's rate limit is used up, then get 429. Actuator health is
 * served by the gateway itself and isn't rate-limited.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayRoutesTest {

    private static final HttpServer ticketing = startBackend("ticketing");

    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void routeToTheStub(DynamicPropertyRegistry registry) {
        registry.add("gateway.ticketing-uri", () -> "http://localhost:" + ticketing.getAddress().getPort());
        registry.add("gateway.rate-limit.capacity", () -> 3);
        registry.add("gateway.rate-limit.period", () -> "1m");
    }

    @AfterAll
    static void stopBackend() {
        ticketing.stop(0);
    }

    // Answers every request with "<name> <method> <path>", so a test can see where the request went.
    private static HttpServer startBackend(String name) {
        try {
            HttpServer server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            server.createContext("/", exchange -> {
                byte[] body = (name + " " + exchange.getRequestMethod() + " " + exchange.getRequestURI())
                        .getBytes(StandardCharsets.UTF_8);
                exchange.sendResponseHeaders(200, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
            return server;
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }

    private HttpResponse<String> get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path)).build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    @Test
    void routesEventsToTicketingUntilTheLimitThenAnswers429() throws Exception {
        for (int i = 0; i < 3; i++) {
            HttpResponse<String> response = get("/events/rock-cluj");
            assertEquals(200, response.statusCode());
            assertEquals("ticketing GET /events/rock-cluj", response.body());
        }

        HttpResponse<String> overTheLimit = get("/events/rock-cluj");
        assertEquals(429, overTheLimit.statusCode());
        assertEquals("0", overTheLimit.headers().firstValue("X-RateLimit-Remaining").orElseThrow());
    }

    @Test
    void healthIsServedByTheGatewayAndNotRateLimited() throws Exception {
        for (int i = 0; i < 5; i++) {
            assertEquals(200, get("/actuator/health").statusCode());
        }
    }
}
