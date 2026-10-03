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
import java.net.URLDecoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Integration test (the gateway on a random port with Cloud Run auth on; the services and Cloud Run's metadata server
 * replaced by tiny JDK HTTP servers): each service receives an ID token for its own URL instead of the client's
 * Authorization header, and the gateway asks the metadata server once per service, not once per request.
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class GatewayCloudRunAuthTest {

    private static final StubBackend ticketing = new StubBackend("ticketing");
    private static final StubBackend notifications = new StubBackend("notifications");
    private static final MetadataServer metadata = new MetadataServer();

    private final HttpClient client = HttpClient.newHttpClient();

    @LocalServerPort
    private int port;

    @DynamicPropertySource
    static void cloudRun(DynamicPropertyRegistry registry) {
        registry.add("gateway.ticketing-uri", ticketing::uri);
        registry.add("gateway.notifications-uri", notifications::uri);
        registry.add("gateway.rate-limit.capacity", () -> 1000);
        registry.add("gateway.cloud-run-auth.enabled", () -> true);
        registry.add("gateway.cloud-run-auth.metadata-uri", metadata::uri);
    }

    @AfterAll
    static void stopServers() {
        ticketing.stop();
        notifications.stop();
        metadata.stop();
    }

    private void get(String path) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(URI.create("http://localhost:" + port + path))
                .header("Authorization", "Bearer forged-by-the-client")
                .build();
        assertEquals(200, client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode());
    }

    @Test
    void eachServiceGetsATokenForItsOwnUrlInsteadOfTheClientsHeader() throws Exception {
        get("/events");
        get("/notifications");

        assertEquals("Bearer id-token-for-" + ticketing.uri(), ticketing.lastAuthorization());
        assertEquals("Bearer id-token-for-" + notifications.uri(), notifications.lastAuthorization());
    }

    @Test
    void aTokenIsFetchedOncePerServiceAndReused() throws Exception {
        get("/events");
        get("/events");
        get("/events");

        assertEquals(1, metadata.calls(ticketing.uri()));
    }

    /**
     * Stands in for Cloud Run's metadata server: answers {@code id-token-for-<audience>}, and refuses requests without
     * the {@code Metadata-Flavor: Google} header, as the real one does.
     */
    private static final class MetadataServer {

        private final HttpServer server;
        private final Map<String, AtomicInteger> calls = new ConcurrentHashMap<>();

        MetadataServer() {
            try {
                server = HttpServer.create(new InetSocketAddress("localhost", 0), 0);
            } catch (IOException e) {
                throw new IllegalStateException(e);
            }
            server.createContext("/computeMetadata/v1/instance/service-accounts/default/identity", exchange -> {
                String audience = URLDecoder.decode(
                        exchange.getRequestURI().getRawQuery().substring("audience=".length()), StandardCharsets.UTF_8);
                boolean fromGoogleClient = "Google".equals(exchange.getRequestHeaders().getFirst("Metadata-Flavor"));
                byte[] body = (fromGoogleClient ? "id-token-for-" + audience : "missing Metadata-Flavor")
                        .getBytes(StandardCharsets.UTF_8);
                calls.computeIfAbsent(audience, key -> new AtomicInteger()).incrementAndGet();
                exchange.sendResponseHeaders(fromGoogleClient ? 200 : 403, body.length);
                try (OutputStream out = exchange.getResponseBody()) {
                    out.write(body);
                }
            });
            server.start();
        }

        String uri() {
            return "http://localhost:" + server.getAddress().getPort();
        }

        int calls(String audience) {
            AtomicInteger count = calls.get(audience);
            return count == null ? 0 : count.get();
        }

        void stop() {
            server.stop(0);
        }
    }
}
