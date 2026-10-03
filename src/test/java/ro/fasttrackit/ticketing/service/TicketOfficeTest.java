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
import ro.fasttrackit.ticketing.domain.Booking;
import ro.fasttrackit.ticketing.domain.BookingRequest;
import ro.fasttrackit.ticketing.domain.BookingResult;
import ro.fasttrackit.ticketing.domain.Event;
import ro.fasttrackit.ticketing.domain.Venue;
import ro.fasttrackit.ticketing.persistence.BookingRepository;
import ro.fasttrackit.ticketing.persistence.EventDocument;
import ro.fasttrackit.ticketing.persistence.EventRepository;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Persistence test (real MongoDB in a Testcontainer, {@code @DataMongoTest} plus the service): checks the booking
 * rules and the queries in {@link TicketOffice} against the repositories.
 */
@DataMongoTest
@Import(TicketOffice.class)
@Testcontainers
class TicketOfficeTest {

    @Container
    @ServiceConnection
    static MongoDBContainer mongo = new MongoDBContainer("mongo:8.0");

    // MongoDB stores dates with millisecond precision, so the fixtures use the same.
    private static final LocalDateTime NOW = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);

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

    private void given(Event... events) {
        Stream.of(events).map(EventDocument::from).forEach(eventRepository::save);
    }

    private static Event event(String id, String name, String city, LocalDateTime startsAt, int capacity, int bookedSeats) {
        return Event.builder()
                .id(id)
                .name(name)
                .venue(new Venue("Arena", city))
                .startsAt(startsAt)
                .capacity(capacity)
                .bookedSeats(bookedSeats)
                .build();
    }

    private static Event event(String id, int capacity, int bookedSeats) {
        return event(id, "Event " + id, "Cluj", NOW.plusDays(7), capacity, bookedSeats);
    }

    private static List<String> ids(List<Event> events) {
        return events.stream().map(Event::getId).toList();
    }

    @Test
    void unknownEventIsReported() {
        given(event("e1", 100, 0));

        BookingResult result = office.book(new BookingRequest("e9", "ana@mail.ro", 2));

        assertEquals(new BookingResult.UnknownEvent("e9"), result);
        assertTrue(office.bookedSeatsPerEvent().isEmpty());
    }

    @Test
    void requestForMoreSeatsThanFreeIsSoldOut() {
        given(event("e1", 10, 8));

        BookingResult result = office.book(new BookingRequest("e1", "ana@mail.ro", 3));

        assertEquals(new BookingResult.SoldOut("e1", 2), result);
        assertEquals(8, office.findEvent("e1").orElseThrow().getBookedSeats());
        assertTrue(office.bookedSeatsPerEvent().isEmpty());
    }

    @Test
    void requestForExactlyTheFreeSeatsIsConfirmed() {
        given(event("e1", 10, 8));

        BookingResult result = office.book(new BookingRequest("e1", "ana@mail.ro", 2));

        assertInstanceOf(BookingResult.Confirmed.class, result);
        assertEquals(0, office.findEvent("e1").orElseThrow().availableSeats());
    }

    @Test
    void confirmedBookingIsStoredAndIncreasesBookedSeats() {
        given(event("e1", 100, 10));

        BookingResult result = office.book(new BookingRequest("e1", "ana@mail.ro", 2));

        Booking booking = assertInstanceOf(BookingResult.Confirmed.class, result).booking();
        assertNotNull(booking.getId());
        assertFalse(booking.getId().isBlank());
        assertEquals("e1", booking.getEventId());
        assertEquals("ana@mail.ro", booking.getCustomerEmail());
        assertEquals(2, booking.getSeats());
        assertNotNull(booking.getBookedAt());
        assertEquals(12, office.findEvent("e1").orElseThrow().getBookedSeats());
    }

    @Test
    void secondBookingAddsToBookedSeats() {
        given(event("e1", 100, 0));

        BookingResult first = office.book(new BookingRequest("e1", "ana@mail.ro", 2));
        BookingResult second = office.book(new BookingRequest("e1", "ion@mail.ro", 3));

        assertInstanceOf(BookingResult.Confirmed.class, first);
        assertInstanceOf(BookingResult.Confirmed.class, second);
        assertEquals(5, office.findEvent("e1").orElseThrow().getBookedSeats());
    }

    @Test
    void duplicateEventIdsAreRejected() {
        office.addEvent(event("e1", 100, 0));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> office.addEvent(event("e1", 50, 0)));

        assertTrue(exception.getMessage().contains("e1"));
    }

    @Test
    void startedEventIsRejected() {
        LocalDateTime startsAt = NOW.minusDays(1);
        given(event("e1", "Concert", "Cluj", startsAt, 100, 0));

        BookingResult result = office.book(new BookingRequest("e1", "ana@mail.ro", 2));

        assertEquals(new BookingResult.AlreadyStarted("e1", startsAt), result);
        assertEquals(0, office.findEvent("e1").orElseThrow().getBookedSeats());
        assertTrue(office.bookedSeatsPerEvent().isEmpty());
    }

    @Test
    void startedEventIsCheckedBeforeSeats() {
        LocalDateTime startsAt = NOW.minusDays(1);
        given(event("e1", "Concert", "Cluj", startsAt, 10, 10));

        BookingResult result = office.book(new BookingRequest("e1", "ana@mail.ro", 2));

        assertEquals(new BookingResult.AlreadyStarted("e1", startsAt), result);
    }

    @Test
    void allEventsReturnsEveryEventSortedByStart() {
        given(event("e1", "Concert", "Cluj", NOW.plusDays(3), 100, 0),
                event("e2", "Festival", "Cluj", NOW.plusDays(1), 10, 10),
                event("e3", "Play", "Iasi", NOW.plusDays(2), 50, 0));

        assertEquals(List.of("e2", "e3", "e1"), ids(office.allEvents()));
    }

    @Test
    void eventsWithFreeSeatsSkipsFullEvents() {
        given(event("e1", 10, 5), event("e2", 10, 10));

        assertEquals(List.of("e1"), ids(office.eventsWithFreeSeats()));
    }

    @Test
    void bookedSeatsPerEventSumsBookingsAndSkipsEventsWithoutBookings() {
        given(event("e1", 100, 0), event("e2", 100, 0), event("e3", 100, 0));
        office.book(new BookingRequest("e1", "ana@mail.ro", 2));
        office.book(new BookingRequest("e1", "ion@mail.ro", 3));
        office.book(new BookingRequest("e2", "ana@mail.ro", 1));

        assertEquals(Map.of("e1", 5, "e2", 1), office.bookedSeatsPerEvent());
    }

    @Test
    void bookedSeatsPerEventIsEmptyWithoutBookings() {
        given(event("e1", 100, 0));

        assertTrue(office.bookedSeatsPerEvent().isEmpty());
    }

    @Test
    void topEventsAreSortedByBookedSeats() {
        given(event("e1", "Concert", "Cluj", NOW.plusDays(1), 100, 5),
                event("e2", "Festival", "Cluj", NOW.plusDays(1), 100, 8),
                event("e3", "Play", "Cluj", NOW.plusDays(1), 100, 0));

        assertEquals(List.of("e2", "e1"), ids(office.topEventsByBookedSeats(2)));
    }

    @Test
    void topEventsBreaksTiesByName() {
        given(event("e1", "Opera", "Cluj", NOW.plusDays(1), 100, 4),
                event("e2", "Ballet", "Cluj", NOW.plusDays(1), 100, 4));

        assertEquals(List.of("e2", "e1"), ids(office.topEventsByBookedSeats(2)));
    }

    @Test
    void topEventsReturnsAllWhenFewerThanRequested() {
        given(event("e1", "Concert", "Cluj", NOW.plusDays(1), 100, 5),
                event("e2", "Festival", "Cluj", NOW.plusDays(1), 100, 8),
                event("e3", "Play", "Cluj", NOW.plusDays(1), 100, 0));

        assertEquals(List.of("e2", "e1", "e3"), ids(office.topEventsByBookedSeats(10)));
    }

    @Test
    void upcomingEventsInCityIgnoresCaseAndPastEvents() {
        given(event("e1", "Concert", "Cluj", NOW.plusDays(1), 100, 0),
                event("e2", "Festival", "Cluj", NOW.minusDays(1), 100, 0),
                event("e3", "Play", "Iasi", NOW.plusDays(1), 100, 0));

        assertEquals(List.of("e1"), ids(office.upcomingEventsIn("cluj", NOW)));
    }

    @Test
    void upcomingEventsExcludesEventStartingNow() {
        given(event("e1", "Concert", "Cluj", NOW, 100, 0));

        assertTrue(office.upcomingEventsIn("Cluj", NOW).isEmpty());
    }

    @Test
    void upcomingEventsAreSortedByStart() {
        given(event("e1", "Concert", "Cluj", NOW.plusDays(3), 100, 0),
                event("e2", "Festival", "Cluj", NOW.plusDays(1), 100, 0));

        assertEquals(List.of("e2", "e1"), ids(office.upcomingEventsIn("Cluj", NOW)));
    }

    @Test
    void cityQueryMatchesTheWholeCityIgnoringCase() {
        given(event("e1", "Concert", "Cluj", NOW.plusDays(1), 100, 0),
                event("e2", "Festival", "Cluj-Napoca", NOW.plusDays(1), 100, 0),
                event("e3", "Play", "Iasi", NOW.plusDays(1), 100, 0));

        assertEquals(List.of("e1"), eventRepository.findByVenueCityIgnoreCase("CLUJ").stream()
                .map(EventDocument::getId)
                .toList());
    }

    @Test
    void addedEventIsListedAndFound() {
        given(event("e1", 100, 0));
        Event added = event("e2", "Event e2", "Cluj", NOW.plusDays(8), 50, 0);

        office.addEvent(added);

        assertEquals(List.of("e1", "e2"), ids(office.allEvents()));
        assertEquals(Optional.of(added), office.findEvent("e2"));
    }

    @Test
    void addingDuplicateEventIdIsRejected() {
        given(event("e1", 100, 10));

        IllegalArgumentException exception = assertThrows(IllegalArgumentException.class,
                () -> office.addEvent(event("e1", 50, 0)));

        assertTrue(exception.getMessage().contains("e1"));
        assertEquals(100, office.findEvent("e1").orElseThrow().getCapacity());
    }

    @Test
    void confirmedBookingIsFoundById() {
        given(event("e1", 100, 0));
        Booking booking = assertInstanceOf(BookingResult.Confirmed.class,
                office.book(new BookingRequest("e1", "ana@mail.ro", 2))).booking();

        assertEquals(Optional.of(booking), office.findBooking(booking.getId()));
    }

    @Test
    void foundBookingEqualsTheConfirmedOneFieldForField() {
        given(event("e1", 100, 0));
        Booking booking = assertInstanceOf(BookingResult.Confirmed.class,
                office.book(new BookingRequest("e1", "ana@mail.ro", 2))).booking();

        Booking found = office.findBooking(booking.getId()).orElseThrow();

        assertEquals(booking.getId(), found.getId());
        assertEquals(booking.getEventId(), found.getEventId());
        assertEquals(booking.getCustomerEmail(), found.getCustomerEmail());
        assertEquals(booking.getSeats(), found.getSeats());
        assertEquals(booking.getBookedAt(), found.getBookedAt());
    }

    @Test
    void unknownBookingIdIsEmpty() {
        given(event("e1", 100, 0));
        office.book(new BookingRequest("e1", "ana@mail.ro", 2));

        assertEquals(Optional.empty(), office.findBooking("b9"));
    }

    @Test
    void twoBookingsAddUpAfterReloadingFromTheDatabase() {
        given(event("e1", 100, 0));
        office.book(new BookingRequest("e1", "ana@mail.ro", 2));
        office.book(new BookingRequest("e1", "ion@mail.ro", 3));

        EventDocument reloaded = eventRepository.findById("e1").orElseThrow();

        assertEquals(5, reloaded.getBookedSeats());
        assertEquals(5, office.findEvent("e1").orElseThrow().getBookedSeats());
        assertEquals(2, bookingRepository.count());
    }
}
