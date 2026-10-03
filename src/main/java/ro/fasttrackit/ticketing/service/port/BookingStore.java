package ro.fasttrackit.ticketing.service.port;

import ro.fasttrackit.ticketing.domain.Booking;

import java.util.Map;
import java.util.Optional;

/**
 * The bookings booking needs, in the domain's language. The adapter decides how they are stored.
 */
public interface BookingStore {

    void save(Booking booking);

    Optional<Booking> findById(String id);

    /** The booked seats summed per event id. */
    Map<String, Integer> bookedSeatsPerEvent();
}
