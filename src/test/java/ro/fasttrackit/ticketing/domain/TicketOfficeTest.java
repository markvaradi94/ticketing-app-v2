package ro.fasttrackit.ticketing.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Unit test (no Spring): checks the booking rules and the stream queries in {@link TicketOffice}.
 */
class TicketOfficeTest {

    private static final LocalDateTime NOW = LocalDateTime.now();

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
        TicketOffice office = new TicketOffice(List.of(event("e1", 100, 0)));

        BookingResult result = office.book(new BookingRequest("e9", "ana@mail.ro", 2));

        assertEquals(new BookingResult.UnknownEvent("e9"), result);
        assertTrue(office.bookedSeatsPerEvent().isEmpty());
    }

    @Test
    void requestForMoreSeatsThanFreeIsSoldOut() {
        TicketOffice office = new TicketOffice(List.of(event("e1", 10, 8)));

        BookingResult result = office.book(new BookingRequest("e1", "ana@mail.ro", 3));

        assertEquals(new BookingResult.SoldOut("e1", 2), result);
        assertEquals(8, office.findEvent("e1").orElseThrow().getBookedSeats());
        assertTrue(office.bookedSeatsPerEvent().isEmpty());
    }

    @Test
    void requestForExactlyTheFreeSeatsIsConfirmed() {
        TicketOffice office = new TicketOffice(List.of(event("e1", 10, 8)));

        BookingResult result = office.book(new BookingRequest("e1", "ana@mail.ro", 2));

        assertInstanceOf(BookingResult.Confirmed.class, result);
        assertEquals(0, office.findEvent("e1").orElseThrow().availableSeats());
    }

    @Test
    void confirmedBookingIsStoredAndIncreasesBookedSeats() {
        TicketOffice office = new TicketOffice(List.of(event("e1", 100, 10)));

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
        TicketOffice office = new TicketOffice(List.of(event("e1", 100, 0)));

        office.book(new BookingRequest("e1", "ana@mail.ro", 2));
        office.book(new BookingRequest("e1", "ion@mail.ro", 3));

        assertEquals(5, office.findEvent("e1").orElseThrow().getBookedSeats());
    }

    @Test
    void startedEventIsRejected() {
        LocalDateTime startsAt = NOW.minusDays(1);
        TicketOffice office = new TicketOffice(List.of(event("e1", "Concert", "Cluj", startsAt, 100, 0)));

        BookingResult result = office.book(new BookingRequest("e1", "ana@mail.ro", 2));

        assertEquals(new BookingResult.AlreadyStarted("e1", startsAt), result);
        assertEquals(0, office.findEvent("e1").orElseThrow().getBookedSeats());
        assertTrue(office.bookedSeatsPerEvent().isEmpty());
    }

    @Test
    void startedEventIsCheckedBeforeSeats() {
        LocalDateTime startsAt = NOW.minusDays(1);
        TicketOffice office = new TicketOffice(List.of(event("e1", "Concert", "Cluj", startsAt, 10, 10)));

        BookingResult result = office.book(new BookingRequest("e1", "ana@mail.ro", 2));

        assertEquals(new BookingResult.AlreadyStarted("e1", startsAt), result);
    }

    @Test
    void eventsWithFreeSeatsSkipsFullEvents() {
        TicketOffice office = new TicketOffice(List.of(event("e1", 10, 5), event("e2", 10, 10)));

        assertEquals(List.of("e1"), ids(office.eventsWithFreeSeats()));
    }

    @Test
    void bookedSeatsPerEventSumsBookingsAndSkipsEventsWithoutBookings() {
        TicketOffice office = new TicketOffice(List.of(event("e1", 100, 0), event("e2", 100, 0), event("e3", 100, 0)));
        office.book(new BookingRequest("e1", "ana@mail.ro", 2));
        office.book(new BookingRequest("e1", "ion@mail.ro", 3));
        office.book(new BookingRequest("e2", "ana@mail.ro", 1));

        assertEquals(Map.of("e1", 5, "e2", 1), office.bookedSeatsPerEvent());
    }

    @Test
    void bookedSeatsPerEventIsEmptyWithoutBookings() {
        TicketOffice office = new TicketOffice(List.of(event("e1", 100, 0)));

        assertTrue(office.bookedSeatsPerEvent().isEmpty());
    }

    @Test
    void topEventsAreSortedByBookedSeats() {
        TicketOffice office = new TicketOffice(List.of(
                event("e1", "Concert", "Cluj", NOW.plusDays(1), 100, 5),
                event("e2", "Festival", "Cluj", NOW.plusDays(1), 100, 8),
                event("e3", "Play", "Cluj", NOW.plusDays(1), 100, 0)));

        assertEquals(List.of("e2", "e1"), ids(office.topEventsByBookedSeats(2)));
    }

    @Test
    void topEventsBreaksTiesByName() {
        TicketOffice office = new TicketOffice(List.of(
                event("e1", "Opera", "Cluj", NOW.plusDays(1), 100, 4),
                event("e2", "Ballet", "Cluj", NOW.plusDays(1), 100, 4)));

        assertEquals(List.of("e2", "e1"), ids(office.topEventsByBookedSeats(2)));
    }

    @Test
    void topEventsReturnsAllWhenFewerThanRequested() {
        TicketOffice office = new TicketOffice(List.of(
                event("e1", "Concert", "Cluj", NOW.plusDays(1), 100, 5),
                event("e2", "Festival", "Cluj", NOW.plusDays(1), 100, 8),
                event("e3", "Play", "Cluj", NOW.plusDays(1), 100, 0)));

        assertEquals(List.of("e2", "e1", "e3"), ids(office.topEventsByBookedSeats(10)));
    }

    @Test
    void upcomingEventsInCityIgnoresCaseAndPastEvents() {
        TicketOffice office = new TicketOffice(List.of(
                event("e1", "Concert", "Cluj", NOW.plusDays(1), 100, 0),
                event("e2", "Festival", "Cluj", NOW.minusDays(1), 100, 0),
                event("e3", "Play", "Iasi", NOW.plusDays(1), 100, 0)));

        assertEquals(List.of("e1"), ids(office.upcomingEventsIn("cluj", NOW)));
    }

    @Test
    void upcomingEventsAreSortedByStart() {
        TicketOffice office = new TicketOffice(List.of(
                event("e1", "Concert", "Cluj", NOW.plusDays(3), 100, 0),
                event("e2", "Festival", "Cluj", NOW.plusDays(1), 100, 0)));

        assertEquals(List.of("e2", "e1"), ids(office.upcomingEventsIn("Cluj", NOW)));
    }
}
