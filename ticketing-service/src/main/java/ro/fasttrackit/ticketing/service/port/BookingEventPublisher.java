package ro.fasttrackit.ticketing.service.port;

import ro.fasttrackit.ticketing.domain.BookingConfirmed;

/**
 * Announces booking events to whoever listens, in the domain's language. The adapter decides how they travel.
 */
public interface BookingEventPublisher {

    /**
     * Announces that a booking was confirmed. Called once per confirmed booking, after it is saved. If it throws,
     * the booking stays saved and the caller sees the exception: saved but not announced (the dual-write gap).
     */
    void publish(BookingConfirmed event);
}
