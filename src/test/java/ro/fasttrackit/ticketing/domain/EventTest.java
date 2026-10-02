package ro.fasttrackit.ticketing.domain;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotSame;

/**
 * Unit test (no Spring): checks {@link Event#availableSeats()} and the {@code toBuilder} copy.
 */
class EventTest {

    private final Event event = Event.builder()
            .id("e1")
            .name("Concert")
            .venue(new Venue("Arena", "Cluj"))
            .startsAt(LocalDateTime.of(2026, 10, 20, 19, 0))
            .capacity(100)
            .bookedSeats(98)
            .build();

    @Test
    void availableSeatsIsCapacityMinusBookedSeats() {
        assertEquals(2, event.availableSeats());
    }

    @Test
    void toBuilderCopiesWithoutChangingTheOriginal() {
        Event copy = event.toBuilder().bookedSeats(99).build();

        assertNotSame(event, copy);
        assertEquals("e1", copy.getId());
        assertEquals(99, copy.getBookedSeats());
        assertEquals(98, event.getBookedSeats());
        assertEquals(event, copy);
    }
}
