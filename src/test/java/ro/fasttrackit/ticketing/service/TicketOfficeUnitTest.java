package ro.fasttrackit.ticketing.service;

import org.junit.jupiter.api.Test;
import ro.fasttrackit.ticketing.domain.Booking;
import ro.fasttrackit.ticketing.domain.BookingConfirmed;
import ro.fasttrackit.ticketing.domain.BookingRequest;
import ro.fasttrackit.ticketing.domain.BookingResult;
import ro.fasttrackit.ticketing.domain.Event;
import ro.fasttrackit.ticketing.domain.Venue;
import ro.fasttrackit.ticketing.service.port.BookingEventPublisher;
import ro.fasttrackit.ticketing.service.port.BookingStore;
import ro.fasttrackit.ticketing.service.port.EventStore;

import java.time.LocalDateTime;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test (no Spring, no Docker): {@link TicketOffice} built on in-memory fakes of its ports. A confirmed booking
 * publishes exactly one {@link BookingConfirmed}; every rejected booking publishes nothing. The fake event store
 * honours the port's versioned {@code trySave}; a lost race is staged as another writer booking just before the
 * next save, so the service's stale copy loses on its version.
 */
class TicketOfficeUnitTest {

    private static final LocalDateTime IN_A_WEEK = LocalDateTime.now().plusDays(7);

    private final FakeEventStore events = new FakeEventStore();
    private final FakeBookingStore bookings = new FakeBookingStore();
    private final RecordingPublisher publisher = new RecordingPublisher();
    private final TicketOffice office = new TicketOffice(events, bookings, publisher);

    private static Event event(String id, LocalDateTime startsAt, int capacity, int bookedSeats) {
        return Event.builder()
                .id(id)
                .name("Concert " + id)
                .venue(new Venue("Arena", "Cluj"))
                .startsAt(startsAt)
                .capacity(capacity)
                .bookedSeats(bookedSeats)
                .version(0L)
                .build();
    }

    @Test
    void confirmedBookingPublishesExactlyOneEventWithTheBookingsFields() {
        events.add(event("e1", IN_A_WEEK, 10, 0));

        BookingResult result = office.book(new BookingRequest("e1", "ana@example.com", 2));

        Booking booking = assertInstanceOf(BookingResult.Confirmed.class, result).booking();
        assertEquals(List.of(new BookingConfirmed(
                booking.getId(), "e1", "ana@example.com", 2, booking.getBookedAt())), publisher.published);
        assertEquals(booking.getBookedAt(), bookings.stored.get(booking.getId()).getBookedAt());
    }

    @Test
    void unknownEventPublishesNothing() {
        BookingResult result = office.book(new BookingRequest("missing", "ana@example.com", 2));

        assertInstanceOf(BookingResult.UnknownEvent.class, result);
        assertTrue(publisher.published.isEmpty());
    }

    @Test
    void alreadyStartedEventPublishesNothing() {
        events.add(event("e1", LocalDateTime.now().minusHours(1), 10, 0));

        BookingResult result = office.book(new BookingRequest("e1", "ana@example.com", 2));

        assertInstanceOf(BookingResult.AlreadyStarted.class, result);
        assertTrue(publisher.published.isEmpty());
    }

    @Test
    void soldOutEventPublishesNothing() {
        events.add(event("e1", IN_A_WEEK, 10, 9));

        BookingResult result = office.book(new BookingRequest("e1", "ana@example.com", 2));

        assertInstanceOf(BookingResult.SoldOut.class, result);
        assertTrue(publisher.published.isEmpty());
    }

    @Test
    void twoLostRacesAreAConflictThatPublishesNothing() {
        events.add(event("e1", IN_A_WEEK, 10, 0));
        events.anotherWriterBooksBeforeNextSave("e1", 1);
        events.anotherWriterBooksBeforeNextSave("e1", 1);

        BookingResult result = office.book(new BookingRequest("e1", "ana@example.com", 2));

        assertInstanceOf(BookingResult.Conflict.class, result);
        assertTrue(publisher.published.isEmpty());
        assertTrue(bookings.stored.isEmpty());
    }

    @Test
    void retryAfterOneLostRacePublishesExactlyOnce() {
        events.add(event("e1", IN_A_WEEK, 10, 0));
        events.anotherWriterBooksBeforeNextSave("e1", 1);

        BookingResult result = office.book(new BookingRequest("e1", "ana@example.com", 2));

        Booking booking = assertInstanceOf(BookingResult.Confirmed.class, result).booking();
        assertEquals(List.of(new BookingConfirmed(
                booking.getId(), "e1", "ana@example.com", 2, booking.getBookedAt())), publisher.published);
        assertEquals(1, bookings.stored.size());
        assertEquals(3, events.findById("e1").orElseThrow().getBookedSeats());
    }

    @Test
    void lostRaceToAWriterWhoTookTheRemainingSeatsIsSoldOutAndPublishesNothing() {
        events.add(event("e1", IN_A_WEEK, 10, 0));
        events.anotherWriterBooksBeforeNextSave("e1", 9);

        BookingResult result = office.book(new BookingRequest("e1", "ana@example.com", 2));

        assertEquals(new BookingResult.SoldOut("e1", 1), result);
        assertTrue(publisher.published.isEmpty());
        assertTrue(bookings.stored.isEmpty());
    }

    /**
     * Events in a map, versioned like the real store: {@code trySave} succeeds only when the event carries the
     * stored version, and then bumps it. Writes queued with {@link #anotherWriterBooksBeforeNextSave} are applied
     * to the stored event just before the next {@code trySave}, as if another request had saved first.
     */
    private static class FakeEventStore implements EventStore {
        private final Map<String, Event> stored = new HashMap<>();
        private final Deque<Runnable> otherWriters = new ArrayDeque<>();

        void add(Event event) {
            insert(event);
        }

        void anotherWriterBooksBeforeNextSave(String eventId, int seats) {
            otherWriters.add(() -> {
                Event current = stored.get(eventId);
                stored.put(eventId, current.toBuilder()
                        .bookedSeats(current.getBookedSeats() + seats)
                        .version(current.getVersion() + 1)
                        .build());
            });
        }

        @Override
        public boolean trySave(Event event) {
            if (!otherWriters.isEmpty()) {
                otherWriters.poll().run();
            }
            Event current = stored.get(event.getId());
            if (event.getVersion() == null) {
                if (current != null) {
                    return false;
                }
                stored.put(event.getId(), event.toBuilder().version(0L).build());
                return true;
            }
            if (current == null || !event.getVersion().equals(current.getVersion())) {
                return false;
            }
            stored.put(event.getId(), event.toBuilder().version(event.getVersion() + 1).build());
            return true;
        }

        @Override
        public Optional<Event> findById(String id) {
            return Optional.ofNullable(stored.get(id));
        }

        @Override
        public List<Event> findAll() {
            throw new UnsupportedOperationException("not needed by these tests");
        }

        @Override
        public void insert(Event event) {
            if (stored.containsKey(event.getId())) {
                throw new IllegalStateException("Duplicate event id " + event.getId());
            }
            stored.put(event.getId(), event.getVersion() == null ? event.toBuilder().version(0L).build() : event);
        }

        @Override
        public boolean existsById(String id) {
            return stored.containsKey(id);
        }

        @Override
        public List<Event> findByCityIgnoreCase(String city) {
            throw new UnsupportedOperationException("not needed by these tests");
        }

        @Override
        public List<Event> topByBookedSeats(int n) {
            throw new UnsupportedOperationException("not needed by these tests");
        }
    }

    /** Bookings in a map. */
    private static class FakeBookingStore implements BookingStore {
        private final Map<String, Booking> stored = new HashMap<>();

        @Override
        public void save(Booking booking) {
            stored.put(booking.getId(), booking);
        }

        @Override
        public Optional<Booking> findById(String id) {
            return Optional.ofNullable(stored.get(id));
        }

        @Override
        public Map<String, Integer> bookedSeatsPerEvent() {
            throw new UnsupportedOperationException("not needed by these tests");
        }
    }

    /** Remembers every published event, in order. */
    private static class RecordingPublisher implements BookingEventPublisher {
        private final List<BookingConfirmed> published = new ArrayList<>();

        @Override
        public void publish(BookingConfirmed event) {
            published.add(event);
        }
    }
}
