package ro.fasttrackit.ticketing.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.RepeatedTest;
import org.junit.jupiter.api.RepetitionInfo;
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
import java.util.TreeMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

/**
 * Persistence test (real MongoDB in a Testcontainer, {@code @DataMongoTest} plus the service), spike only:
 * measures how often concurrent bookings overbook an event. It logs its numbers and asserts nothing, so it
 * never fails the build. Entry 3 adopts it as the asserting concurrency test or removes it.
 */
@DataMongoTest
@Import(TicketOffice.class)
@Testcontainers
class OverbookingRaceTest {

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

    @RepeatedTest(10)
    void concurrentBookingsAgainstOneEvent(RepetitionInfo repetition) throws Exception {
        eventRepository.save(EventDocument.from(Event.builder()
                .id("race")
                .name("Race Night")
                .venue(new Venue("Arena", "Cluj"))
                .startsAt(LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS).plusDays(7))
                .capacity(CAPACITY)
                .bookedSeats(0)
                .build()));

        CountDownLatch ready = new CountDownLatch(THREADS);
        CountDownLatch start = new CountDownLatch(1);
        List<Future<String>> outcomes = new ArrayList<>();
        try (ExecutorService pool = Executors.newFixedThreadPool(THREADS)) {
            for (int i = 0; i < THREADS; i++) {
                String email = "fan" + i + "@mail.ro";
                outcomes.add(pool.submit(() -> {
                    ready.countDown();
                    start.await();
                    try {
                        return office.book(new BookingRequest("race", email, 1)).getClass().getSimpleName();
                    } catch (RuntimeException e) {
                        return e.getClass().getSimpleName();
                    }
                }));
            }
            ready.await(10, TimeUnit.SECONDS);
            start.countDown();
        }

        List<String> results = new ArrayList<>();
        for (Future<String> outcome : outcomes) {
            results.add(outcome.get());
        }
        int bookedSeats = eventRepository.findById("race").orElseThrow().getBookedSeats();
        int seatsInBookings = bookingRepository.findAll().stream().mapToInt(BookingDocument::getSeats).sum();
        long confirmed = results.stream().filter(BookingResult.Confirmed.class.getSimpleName()::equals).count();
        boolean overbooked = seatsInBookings > CAPACITY || seatsInBookings != bookedSeats;

        System.out.printf("RACE run %d/%d: overbooked=%s seatsInBookings=%d bookedSeats=%d capacity=%d confirmed=%d outcomes=%s%n",
                repetition.getCurrentRepetition(), repetition.getTotalRepetitions(), overbooked,
                seatsInBookings, bookedSeats, CAPACITY, confirmed, tally(results));
    }

    private static String tally(List<String> results) {
        return results.stream()
                .collect(Collectors.groupingBy(r -> r, TreeMap::new, Collectors.counting()))
                .toString();
    }
}
