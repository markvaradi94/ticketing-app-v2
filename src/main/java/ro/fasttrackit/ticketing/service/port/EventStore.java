package ro.fasttrackit.ticketing.service.port;

import ro.fasttrackit.ticketing.domain.Event;

import java.util.List;
import java.util.Optional;

/**
 * The events booking needs, in the domain's language. The adapter decides how they are stored.
 */
public interface EventStore {

    Optional<Event> findById(String id);

    List<Event> findAll();

    void insert(Event event);

    boolean existsById(String id);

    List<Event> findByCityIgnoreCase(String city);

    /** The {@code n} events with the most booked seats; {@code n} must be greater than zero. */
    List<Event> topByBookedSeats(int n);

    /** Saves the event; returns {@code false} when someone else changed it first (a lost race). */
    boolean trySave(Event event);
}
