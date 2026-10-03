package ro.fasttrackit.ticketing.persistence;

import org.junit.jupiter.api.Test;
import ro.fasttrackit.ticketing.domain.Booking;
import ro.fasttrackit.ticketing.domain.Event;
import ro.fasttrackit.ticketing.domain.Venue;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test (no Spring, no database): checks that {@link EventDocument} and {@link BookingDocument}
 * map to and from the domain without losing a field.
 */
class DocumentMappingTest {

    @Test
    void eventSurvivesTheRoundTrip() {
        Event event = Event.builder()
                .id("e1")
                .name("Rock Night")
                .venue(new Venue("BT Arena", "Cluj"))
                .startsAt(LocalDateTime.of(2030, 6, 12, 20, 0))
                .capacity(500)
                .bookedSeats(12)
                .build();

        Event mapped = EventDocument.from(event).toDomain();

        assertEquals(event.getId(), mapped.getId());
        assertEquals(event.getName(), mapped.getName());
        assertEquals(event.getVenue(), mapped.getVenue());
        assertEquals(event.getStartsAt(), mapped.getStartsAt());
        assertEquals(event.getCapacity(), mapped.getCapacity());
        assertEquals(event.getBookedSeats(), mapped.getBookedSeats());
    }

    @Test
    void bookingSurvivesTheRoundTrip() {
        Booking booking = Booking.builder()
                .id("b1")
                .eventId("e1")
                .customerEmail("ana@mail.ro")
                .seats(2)
                .bookedAt(LocalDateTime.of(2030, 5, 1, 10, 30, 15))
                .build();

        Booking mapped = BookingDocument.from(booking).toDomain();

        assertEquals(booking.getId(), mapped.getId());
        assertEquals(booking.getEventId(), mapped.getEventId());
        assertEquals(booking.getCustomerEmail(), mapped.getCustomerEmail());
        assertEquals(booking.getSeats(), mapped.getSeats());
        assertEquals(booking.getBookedAt(), mapped.getBookedAt());
    }
}
