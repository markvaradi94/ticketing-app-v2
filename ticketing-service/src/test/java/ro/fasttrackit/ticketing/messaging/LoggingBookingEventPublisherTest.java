package ro.fasttrackit.ticketing.messaging;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Unit test (no Spring): the email masking that {@link LoggingBookingEventPublisher} applies to its log line.
 */
class LoggingBookingEventPublisherTest {

    @Test
    void keepsTheFirstCharacterAndTheDomain() {
        assertEquals("a***@mail.ro", LoggingBookingEventPublisher.maskEmail("ana@mail.ro"));
    }

    @Test
    void masksEverythingWithoutALocalPartBeforeAnAt() {
        assertEquals("***", LoggingBookingEventPublisher.maskEmail("not-an-email"));
        assertEquals("***", LoggingBookingEventPublisher.maskEmail("@mail.ro"));
        assertEquals("***", LoggingBookingEventPublisher.maskEmail(""));
        assertEquals("***", LoggingBookingEventPublisher.maskEmail(null));
    }
}
