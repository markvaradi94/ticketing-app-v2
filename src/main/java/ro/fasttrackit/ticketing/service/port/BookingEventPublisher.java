package ro.fasttrackit.ticketing.service.port;

import ro.fasttrackit.ticketing.domain.BookingConfirmed;

/**
 * Announces booking events to whoever listens, in the domain's language. The adapter decides how they travel.
 */
public interface BookingEventPublisher {

    /** Announces that a booking was confirmed. Called once per confirmed booking, after it is saved. */
    void publish(BookingConfirmed event);
}
