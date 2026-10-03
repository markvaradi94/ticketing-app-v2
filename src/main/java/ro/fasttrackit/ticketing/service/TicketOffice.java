package ro.fasttrackit.ticketing.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ro.fasttrackit.ticketing.domain.Booking;
import ro.fasttrackit.ticketing.domain.BookingRequest;
import ro.fasttrackit.ticketing.domain.BookingResult;
import ro.fasttrackit.ticketing.domain.Event;
import ro.fasttrackit.ticketing.service.port.BookingStore;
import ro.fasttrackit.ticketing.service.port.EventStore;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketOffice {

    private final EventStore events;
    private final BookingStore bookings;

    // A lost race is retried once with a fresh read; a second loss is reported as a Conflict.
    public BookingResult book(BookingRequest request) {
        BookingResult result = tryToBook(request);
        if (result instanceof BookingResult.Conflict) {
            result = tryToBook(request);
        }
        return result;
    }

    private BookingResult tryToBook(BookingRequest request) {
        Event event = events.findById(request.eventId()).orElse(null);
        if (event == null) {
            return new BookingResult.UnknownEvent(request.eventId());
        }
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        if (!event.getStartsAt().isAfter(now)) {
            return new BookingResult.AlreadyStarted(event.getId(), event.getStartsAt());
        }
        if (request.seats() > event.availableSeats()) {
            return new BookingResult.SoldOut(event.getId(), event.availableSeats());
        }

        Event updated = event.toBuilder()
                .bookedSeats(event.getBookedSeats() + request.seats())
                .build();
        if (!events.trySave(updated)) {
            return new BookingResult.Conflict(event.getId());
        }

        Booking booking = Booking.builder()
                .id(UUID.randomUUID().toString())
                .eventId(event.getId())
                .customerEmail(request.customerEmail())
                .seats(request.seats())
                .bookedAt(now)
                .build();
        bookings.save(booking);

        return new BookingResult.Confirmed(booking);
    }

    public void addEvent(Event event) {
        if (events.existsById(event.getId())) {
            throw new IllegalArgumentException("Duplicate event id " + event.getId());
        }
        events.insert(event);
    }

    public Optional<Booking> findBooking(String id) {
        return bookings.findById(id);
    }

    public Optional<Event> findEvent(String id) {
        return events.findById(id);
    }

    public List<Event> allEvents() {
        return events.findAll().stream()
                .sorted(Comparator.comparing(Event::getStartsAt))
                .toList();
    }

    public List<Event> eventsWithFreeSeats() {
        return allEvents().stream()
                .filter(event -> event.availableSeats() > 0)
                .toList();
    }

    public Map<String, Integer> bookedSeatsPerEvent() {
        return bookings.bookedSeatsPerEvent();
    }

    public List<Event> topEventsByBookedSeats(int n) {
        if (n < 0) {
            throw new IllegalArgumentException("n must not be negative: " + n);
        }
        if (n == 0) {
            // A store query with limit 0 may mean "no limit", so zero events is answered here.
            return List.of();
        }
        return events.topByBookedSeats(n);
    }

    public List<Event> upcomingEventsIn(String city, LocalDateTime now) {
        return events.findByCityIgnoreCase(city).stream()
                .filter(event -> event.getStartsAt().isAfter(now))
                .sorted(Comparator.comparing(Event::getStartsAt))
                .toList();
    }
}
