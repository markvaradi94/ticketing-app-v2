package ro.fasttrackit.ticketing.service.port;

import ro.fasttrackit.ticketing.domain.Booking;

import java.util.Map;
import java.util.Optional;

/**
 * What the booking use case needs to read and store bookings, in the domain's language. The adapter decides how
 * they are stored.
 */
public interface BookingStore {

    /** Stores the booking; a booking with an id that already exists replaces the stored one. */
    void save(Booking booking);

    Optional<Booking> findById(String id);

    /** The booked seats summed per event id. */
    Map<String, Integer> bookedSeatsPerEvent();
}
