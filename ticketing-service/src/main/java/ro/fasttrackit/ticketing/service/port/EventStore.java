package ro.fasttrackit.ticketing.service.port;

import ro.fasttrackit.ticketing.domain.Event;

import java.util.List;
import java.util.Optional;

/**
 * What the booking use case needs to read and store events, in the domain's language. The adapter decides how
 * they are stored.
 */
public interface EventStore {

    Optional<Event> findById(String id);

    List<Event> findAll();

    /**
     * Stores a new event. An id that already exists fails with an exception from the store, so callers check
     * {@link #existsById} first.
     */
    void insert(Event event);

    boolean existsById(String id);

    List<Event> findByCityIgnoreCase(String city);

    /**
     * The first {@code n} events ordered by booked seats descending, then name ascending, then id ascending.
     * {@code n} must be greater than zero.
     */
    List<Event> topByBookedSeats(int n);

    /**
     * Saves the event conditionally. The event must carry the version it was read with; that version is what makes
     * the save conditional. Returns {@code false} when someone else changed the event first (a lost race), or when
     * it no longer exists. A {@code null} version means the event is new, and it is inserted.
     */
    boolean trySave(Event event);
}
