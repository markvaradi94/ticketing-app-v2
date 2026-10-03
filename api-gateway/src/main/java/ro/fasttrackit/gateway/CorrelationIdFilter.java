package ro.fasttrackit.gateway;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletRequestWrapper;
import jakarta.servlet.http.HttpServletResponse;
import org.jspecify.annotations.NonNull;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.UUID;

/**
 * Gives every request a correlation id: the client's {@code X-Correlation-Id}, or a new one. The id goes to the
 * service with the forwarded request, back to the client in the response, and into this gateway's logs.
 */
@Component
public class CorrelationIdFilter extends OncePerRequestFilter {

    public static final String HEADER = "X-Correlation-Id";
    public static final String MDC_KEY = "correlationId";

    private static final Logger log = LoggerFactory.getLogger(CorrelationIdFilter.class);

    @Override
    protected void doFilterInternal(@NonNull HttpServletRequest request, @NonNull HttpServletResponse response,
                                    @NonNull FilterChain chain) throws ServletException, IOException {
        String correlationId = request.getHeader(HEADER);
        if (correlationId == null || correlationId.isBlank()) {
            correlationId = UUID.randomUUID().toString();
        }
        MDC.put(MDC_KEY, correlationId);
        response.setHeader(HEADER, correlationId);
        try {
            chain.doFilter(withCorrelationId(request, correlationId), response);
            log.info("{} {} -> {}", request.getMethod(), request.getRequestURI(), response.getStatus());
        } finally {
            // Threads are reused: an id left in the MDC would show up in the next request's logs.
            MDC.remove(MDC_KEY);
        }
    }

    // The gateway forwards the request's headers to the service, so a generated id is added to the request itself.
    private static HttpServletRequest withCorrelationId(HttpServletRequest request, String correlationId) {
        if (correlationId.equals(request.getHeader(HEADER))) {
            return request;
        }
        return new HttpServletRequestWrapper(request) {
            @Override
            public String getHeader(String name) {
                return HEADER.equalsIgnoreCase(name) ? correlationId : super.getHeader(name);
            }

            @Override
            public Enumeration<String> getHeaders(String name) {
                return HEADER.equalsIgnoreCase(name) ? Collections.enumeration(List.of(correlationId)) : super.getHeaders(name);
            }

            @Override
            public Enumeration<String> getHeaderNames() {
                List<String> names = Collections.list(super.getHeaderNames());
                names.add(HEADER);
                return Collections.enumeration(names);
            }
        };
    }
}
