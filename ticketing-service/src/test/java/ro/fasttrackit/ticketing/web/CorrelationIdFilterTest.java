package ro.fasttrackit.ticketing.web;

import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.Test;
import org.slf4j.MDC;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Unit test (no Spring context, Spring's servlet mocks): the filter puts the request's correlation id in the MDC for
 * the rest of the request, makes one up when there is none, and leaves the MDC clean afterwards. It doesn't add a
 * response header: the gateway does, and two would reach the client.
 */
class CorrelationIdFilterTest {

    private final CorrelationIdFilter filter = new CorrelationIdFilter();
    private final AtomicReference<String> seenInTheMdc = new AtomicReference<>();
    private final FilterChain rememberTheMdc = (request, response) -> seenInTheMdc.set(MDC.get(CorrelationIdFilter.MDC_KEY));

    @Test
    void usesTheIncomingIdForTheRequest() throws Exception {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/events");
        request.addHeader(CorrelationIdFilter.HEADER, "from-the-gateway");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, rememberTheMdc);

        assertEquals("from-the-gateway", seenInTheMdc.get());
        assertNull(response.getHeader(CorrelationIdFilter.HEADER), "only the gateway returns the id");
        assertNull(MDC.get(CorrelationIdFilter.MDC_KEY), "the id leaked out of the request");
    }

    @Test
    void makesUpAnIdForADirectCall() throws Exception {
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(new MockHttpServletRequest("GET", "/events"), response, rememberTheMdc);

        assertEquals(36, seenInTheMdc.get().length(), "expected a UUID, got " + seenInTheMdc.get());
        assertNull(MDC.get(CorrelationIdFilter.MDC_KEY), "the id leaked out of the request");
    }
}
