package ro.fasttrackit.ticketing.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.mongodb.test.autoconfigure.DataMongoTest;
import org.springframework.boot.testcontainers.service.connection.ServiceConnection;
import org.springframework.context.annotation.Import;
import org.testcontainers.junit.jupiter.Container;
import org.testcontainers.junit.jupiter.Testcontainers;
import org.testcontainers.mongodb.MongoDBContainer;
import ro.fasttrackit.ticketing.domain.BookingRequest;
import ro.fasttrackit.ticketing.domain.BookingResult;
import ro.fasttrackit.ticketing.domain.Event;
import ro.fasttrackit.ticketing.domain.Venue;
import ro.fasttrackit.ticketing.persistence.BookingDocument;
import ro.fasttrackit.ticketing.persistence.BookingRepository;
import ro.fasttrackit.ticketing.persistence.EventDocument;
import ro.fasttrackit.ticketing.persistence.EventRepository;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Persistence test (real MongoDB in a Testcontainer, {@code @DataMongoTest} plus the service): 20 concurrent
 * one-seat bookings against a capacity-10 event never overbook. Remove {@code @Version} from
 * {@link EventDocument} and it fails.
 */
@DataMongoTest
@Import(TicketOffice.class)
@Testcontainers
class ConcurrentBookingTest {

    private static final int CAPACITY = 10;
    private static final int THREADS = 20;

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:8.0");

    @Autowired
    private TicketOffice office;

    @Autowired
    private EventRepository eventRepository;

    @Autowired
    private BookingRepository bookingRepository;

    @BeforeEach
    void emptyCollections() {
        bookingRepository.deleteAll();
        eventRepository.deleteAll();
    }

    @Test
    void concurrentBookingsNeverExceedCapacity() throws Exception {
        eventRepository.save(EventDocument.from(Event.builder()
                .id("e1")
                .name("Rock Night")
                .venue(new Venue("Arena", "Cluj"))
                .startsAt(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS).plusDays(7))
                .capacity(CAPACITY)
                .bookedSeats(0)
                .build()));

        // Every thread waits at the start gate, so all 20 bookings are released at the same moment.
        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<BookingResult>> futures = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
            for (int i = 0; i < THREADS; i++) {
                BookingRequest request = new BookingRequest("e1", "fan" + i + "@mail.ro", 1);
                futures.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    return office.book(request);
                }));
            }
            try {
                assertTrue(ready.await(10, TimeUnit.SECONDS), "all threads at the start gate");
            } finally {
                // Always open the gate, so a failed wait ends the test instead of hanging pool.close().
                start.countDown();
            }
        }

        long confirmed = 0;
        for (Future<BookingResult> future : futures) {
            if (future.get() instanceof BookingResult.Confirmed) {
                confirmed++;
            }
        }
        int bookedSeats = eventRepository.findById("e1").orElseThrow().getBookedSeats();
        int seatsInBookings = bookingRepository.findAll().stream().mapToInt(BookingDocument::getSeats).sum();

        // No assertion that the event fills up: a booking that loses the race twice gets a Conflict.
        assertTrue(seatsInBookings <= CAPACITY, "booked " + seatsInBookings + " seats of " + CAPACITY);
        assertEquals(seatsInBookings, bookedSeats);
        assertEquals(confirmed, seatsInBookings);
        assertTrue(confirmed >= 1, "at least one booking is confirmed");
    }
}
