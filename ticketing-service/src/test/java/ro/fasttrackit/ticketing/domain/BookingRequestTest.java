package ro.fasttrackit.ticketing.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test (no Spring): checks the validation in the {@link BookingRequest} record.
 */
class BookingRequestTest {

    @Test
    void createsValidRequest() {
        BookingRequest request = new BookingRequest("e1", "ana@mail.ro", 2);

        assertEquals("e1", request.eventId());
        assertEquals("ana@mail.ro", request.customerEmail());
        assertEquals(2, request.seats());
    }

    @Test
    void rejectsZeroSeats() {
        assertThrows(IllegalArgumentException.class, () -> new BookingRequest("e1", "ana@mail.ro", 0));
    }

    @Test
    void rejectsNegativeSeats() {
        assertThrows(IllegalArgumentException.class, () -> new BookingRequest("e1", "ana@mail.ro", -1));
    }

    @Test
    void rejectsBlankEmail() {
        assertThrows(IllegalArgumentException.class, () -> new BookingRequest("e1", " ", 2));
    }

    @Test
    void rejectsNullEmail() {
        assertThrows(IllegalArgumentException.class, () -> new BookingRequest("e1", null, 2));
    }

    @Test
    void rejectsBlankEventId() {
        assertThrows(IllegalArgumentException.class, () -> new BookingRequest(" ", "ana@mail.ro", 2));
    }

    @Test
    void rejectsNullEventId() {
        assertThrows(IllegalArgumentException.class, () -> new BookingRequest(null, "ana@mail.ro", 2));
    }
}
