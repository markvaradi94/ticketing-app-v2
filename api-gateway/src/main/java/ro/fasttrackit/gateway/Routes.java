package ro.fasttrackit.gateway;

import com.github.benmanes.caffeine.cache.Caffeine;
import io.github.bucket4j.caffeine.Bucket4jCaffeine;
import io.github.bucket4j.distributed.proxy.AsyncProxyManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.function.HandlerFilterFunction;
import org.springframework.web.servlet.function.RouterFunction;
import org.springframework.web.servlet.function.ServerRequest;
import org.springframework.web.servlet.function.ServerResponse;

import static org.springframework.cloud.gateway.server.mvc.filter.BeforeFilterFunctions.uri;
import static org.springframework.cloud.gateway.server.mvc.filter.Bucket4jFilterFunctions.rateLimit;
import static org.springframework.cloud.gateway.server.mvc.handler.GatewayRouterFunctions.route;
import static org.springframework.cloud.gateway.server.mvc.handler.HandlerFunctions.http;
import static org.springframework.cloud.gateway.server.mvc.predicate.GatewayRequestPredicates.path;

/**
 * The gateway's routes, in the Java DSL of Gateway Server WebMVC (not the WebFlux {@code RouteLocatorBuilder}).
 * Every route is rate-limited per client IP.
 */
@Configuration
public class Routes {

    // In-memory token buckets, one per client key. In memory means per instance: two gateway instances allow
    // twice the rate.
    @Bean
    AsyncProxyManager<String> rateLimitBuckets() {
        return Bucket4jCaffeine.<String>builderFor(Caffeine.newBuilder().maximumSize(100_000)).build().asAsync();
    }

    @Bean
    RouterFunction<ServerResponse> ticketingRoute(GatewayProperties properties) {
        return route("ticketing")
                .route(path("/events/**"), http())
                .before(uri(properties.ticketingUri()))
                .filter(perClientRateLimit(properties.rateLimit()))
                .build();
    }

    private static HandlerFilterFunction<ServerResponse, ServerResponse> perClientRateLimit(GatewayProperties.RateLimit limit) {
        return rateLimit(config -> config
                .setCapacity(limit.capacity())
                .setPeriod(limit.period())
                .setKeyResolver(Routes::clientIp));
    }

    private static String clientIp(ServerRequest request) {
        return request.remoteAddress()
                .map(address -> address.getAddress().getHostAddress())
                .orElse("unknown");
    }
}
