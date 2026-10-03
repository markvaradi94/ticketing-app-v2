package ro.fasttrackit.gateway;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.servlet.function.ServerRequest;

import java.net.URI;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

/**
 * Service-to-service authentication on Cloud Run. The backends accept only callers with an ID token whose audience
 * is their own URL. The gateway asks Cloud Run's metadata server for one, as its own service account, and sends it
 * as {@code Authorization: Bearer ...}. A token is valid for an hour, so it is reused for 50 minutes.
 */
@Component
public class IdentityTokens {

    private static final Duration REUSE_FOR = Duration.ofMinutes(50);

    private final boolean enabled;
    private final RestClient metadataServer;
    private final Map<URI, Token> tokens = new ConcurrentHashMap<>();

    public IdentityTokens(GatewayProperties properties) {
        this.enabled = properties.cloudRunAuth().enabled();
        this.metadataServer = RestClient.builder()
                .baseUrl(properties.cloudRunAuth().metadataUri().toString())
                .defaultHeader("Metadata-Flavor", "Google")
                .build();
    }

    /**
     * A before-filter for the route to {@code backend}: on Cloud Run it replaces the request's {@code Authorization}
     * header with an ID token for that backend, so a client can't send its own; elsewhere it changes nothing.
     */
    public Function<ServerRequest, ServerRequest> authorizeFor(URI backend) {
        if (!enabled) {
            return request -> request;
        }
        return request -> ServerRequest.from(request)
                .headers(headers -> headers.setBearerAuth(tokenFor(backend)))
                .build();
    }

    private String tokenFor(URI audience) {
        Token token = tokens.get(audience);
        if (token == null || token.fetchedAt().plus(REUSE_FOR).isBefore(Instant.now())) {
            token = new Token(fetch(audience), Instant.now());
            tokens.put(audience, token);
        }
        return token.value();
    }

    private String fetch(URI audience) {
        return metadataServer.get()
                .uri(uri -> uri.path("/computeMetadata/v1/instance/service-accounts/default/identity")
                        .queryParam("audience", audience)
                        .build())
                .retrieve()
                .body(String.class);
    }

    private record Token(String value, Instant fetchedAt) {
    }
}
