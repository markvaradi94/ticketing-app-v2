package ro.fasttrackit.ticketing.messaging;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ro.fasttrackit.ticketing.domain.BookingConfirmed;
import ro.fasttrackit.ticketing.service.port.BookingEventPublisher;

/**
 * Interim adapter for {@link BookingEventPublisher}: it only logs the event, one INFO line with all its fields and
 * the customer email masked (the event itself keeps the full address).
 * Session 6 deletes it and replaces it with the RabbitMQ adapter, so exactly one bean implements the port.
 */
@Slf4j
@Component
public class LoggingBookingEventPublisher implements BookingEventPublisher {

    @Override
    public void publish(BookingConfirmed event) {
        log.info("BookingConfirmed bookingId={} eventId={} customerEmail={} seats={} bookedAt={}",
                event.bookingId(), event.eventId(), maskEmail(event.customerEmail()), event.seats(), event.bookedAt());
    }

    /** Keeps the first character and the domain: {@code ana@mail.ro} becomes {@code a***@mail.ro}. */
    static String maskEmail(String email) {
        int at = email == null ? -1 : email.indexOf('@');
        if (at < 1) {
            return "***";
        }
        return email.charAt(0) + "***" + email.substring(at);
    }
}
