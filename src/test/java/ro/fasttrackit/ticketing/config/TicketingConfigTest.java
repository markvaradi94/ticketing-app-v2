package ro.fasttrackit.ticketing.config;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import ro.fasttrackit.ticketing.domain.Event;
import ro.fasttrackit.ticketing.domain.TicketOffice;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test ({@code @SpringBootTest}, full Spring context): checks the {@link TicketOffice} bean
 * that {@link TicketingConfig} builds under the {@code dev} and the default profile.
 */
class TicketingConfigTest {

    @Nested
    @SpringBootTest
    @ActiveProfiles("dev")
    class DevProfile {

        @Autowired
        private TicketOffice ticketOffice;

        @Test
        void seedsTheThreeDemoEvents() {
            List<Event> events = ticketOffice.allEvents();

            assertEquals(List.of("rock-cluj", "jazz-cluj", "opera-iasi"), events.stream().map(Event::getId).toList());
            assertTrue(events.stream().allMatch(event -> event.getBookedSeats() == 0));
            assertTrue(events.stream().allMatch(event -> event.getStartsAt().getYear() == 2030));

            Event opera = ticketOffice.findEvent("opera-iasi").orElseThrow();
            assertEquals("La Traviata", opera.getName());
            assertEquals("Iasi National Opera", opera.getVenue().name());
            assertEquals("Iasi", opera.getVenue().city());
            assertEquals(350, opera.getCapacity());
        }
    }

    @Nested
    @SpringBootTest
    class DefaultProfile {

        @Autowired
        private TicketOffice ticketOffice;

        @Test
        void startsWithNoEvents() {
            assertTrue(ticketOffice.allEvents().isEmpty());
        }
    }
}
