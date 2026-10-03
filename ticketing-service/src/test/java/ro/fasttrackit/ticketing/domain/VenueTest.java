package ro.fasttrackit.ticketing.domain;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

/**
 * Unit test (no Spring): checks the validation in the {@link Venue} record.
 */
class VenueTest {

    @Test
    void createsValidVenue() {
        Venue venue = new Venue("Arena", "Cluj");

        assertEquals("Arena", venue.name());
        assertEquals("Cluj", venue.city());
    }

    @Test
    void rejectsBlankName() {
        assertThrows(IllegalArgumentException.class, () -> new Venue(" ", "Cluj"));
    }

    @Test
    void rejectsNullName() {
        assertThrows(IllegalArgumentException.class, () -> new Venue(null, "Cluj"));
    }

    @Test
    void rejectsBlankCity() {
        assertThrows(IllegalArgumentException.class, () -> new Venue("Arena", " "));
    }

    @Test
    void rejectsNullCity() {
        assertThrows(IllegalArgumentException.class, () -> new Venue("Arena", null));
    }
}
