package ro.fasttrackit.ticketing.config;

import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.DefaultApplicationArguments;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import ro.fasttrackit.ticketing.domain.BookingRequest;
import ro.fasttrackit.ticketing.domain.BookingResult;
import ro.fasttrackit.ticketing.domain.Event;
import ro.fasttrackit.ticketing.persistence.EventRepository;
import ro.fasttrackit.ticketing.service.TicketOffice;
import ro.fasttrackit.ticketing.service.port.BookingEventPublisher;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Integration test ({@code @SpringBootTest}, full Spring context on a MongoDB Testcontainer): checks that
 * {@link DevDataSeeder} seeds the events under the {@code dev} profile only when the collection is empty,
 * and seeds nothing under the default profile. Each profile gets its own container, so they don't share data.
 */
class DevDataSeederTest {

    @Nested
    @SpringBootTest
    @ActiveProfiles("dev")
    @Testcontainers
    class DevProfile {

        @Container
        @ServiceConnection
        static MongoDBContainer mongo = new MongoDBContainer("mongo:8.0");

        // The test books once; publishing is tested on its own, so no broker is needed here.
        @MockitoBean
        private BookingEventPublisher publisher;

        @Autowired
        private TicketOffice ticketOffice;

        @Autowired
        private DevDataSeeder seeder;

        @Autowired
        private EventRepository eventRepository;

        @Test
        void seedsOnlyWhenTheCollectionIsEmpty() {
            // first run: at startup, against an empty collection
            List<Event> events = ticketOffice.allEvents();
            assertEquals(List.of("rock-cluj", "jazz-cluj", "opera-iasi"), events.stream().map(Event::getId).toList());
            assertTrue(events.stream().allMatch(event -> event.getBookedSeats() == 0));
            assertTrue(events.stream().allMatch(event -> event.getStartsAt().getYear() == 2030));

            Event opera = ticketOffice.findEvent("opera-iasi").orElseThrow();
            assertEquals("La Traviata", opera.getName());
            assertEquals("Iasi National Opera", opera.getVenue().name());
            assertEquals("Iasi", opera.getVenue().city());
            assertEquals(350, opera.getCapacity());

            assertInstanceOf(BookingResult.Confirmed.class,
                    ticketOffice.book(new BookingRequest("rock-cluj", "ana@mail.ro", 2)));

            // second run: like a restart, the collection is no longer empty
            seeder.run(new DefaultApplicationArguments());

            assertEquals(3, eventRepository.count());
            assertEquals(2, ticketOffice.findEvent("rock-cluj").orElseThrow().getBookedSeats());
        }
    }

    @Nested
    @SpringBootTest
    @Testcontainers
    class DefaultProfile {

        @Container
        @ServiceConnection
        static MongoDBContainer mongo = new MongoDBContainer("mongo:8.0");

        @Autowired
        private TicketOffice ticketOffice;

        @Test
        void startsWithNoEvents() {
            assertTrue(ticketOffice.allEvents().isEmpty());
        }
    }
}
