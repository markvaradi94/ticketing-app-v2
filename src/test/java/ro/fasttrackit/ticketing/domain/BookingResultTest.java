package ro.fasttrackit.ticketing.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test (no Spring): checks the messages from {@link BookingResult#describe()}.
 */
class BookingResultTest {

    @Test
    void describesConfirmed() {
        Booking booking = Booking.builder()
                .id("b1")
                .eventId("e1")
                .customerEmail("ana@mail.ro")
                .seats(2)
                .bookedAt(LocalDateTime.of(2026, 10, 12, 10, 0))
                .build();

        assertEquals("Booking b1 confirmed: 2 seats", new BookingResult.Confirmed(booking).describe());
    }

    @Test
    void describesSoldOut() {
        assertEquals("Event e1 has only 1 seats left", new BookingResult.SoldOut("e1", 1).describe());
    }

    @Test
    void describesUnknownEvent() {
        assertEquals("No event with id e9", new BookingResult.UnknownEvent("e9").describe());
    }
}
