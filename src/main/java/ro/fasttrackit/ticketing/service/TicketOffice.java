package ro.fasttrackit.ticketing.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ro.fasttrackit.ticketing.domain.Booking;
import ro.fasttrackit.ticketing.domain.BookingRequest;
import ro.fasttrackit.ticketing.domain.BookingResult;
import ro.fasttrackit.ticketing.domain.Event;
import ro.fasttrackit.ticketing.persistence.BookingDocument;
import ro.fasttrackit.ticketing.persistence.BookingRepository;
import ro.fasttrackit.ticketing.persistence.EventDocument;
import ro.fasttrackit.ticketing.persistence.EventRepository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class TicketOffice {

    private static final String LAB = "Lab: back this with the repositories";

    private final EventRepository events;
    private final BookingRepository bookings;

    public BookingResult book(BookingRequest request) {
        EventDocument doc = events.findById(request.eventId()).orElse(null);
        if (doc == null) {
            return new BookingResult.UnknownEvent(request.eventId());
        }
        Event event = doc.toDomain();
        LocalDateTime now = LocalDateTime.now();
        if (!event.getStartsAt().isAfter(now)) {
            return new BookingResult.AlreadyStarted(event.getId(), event.getStartsAt());
        }
        if (request.seats() > event.availableSeats()) {
            return new BookingResult.SoldOut(event.getId(), event.availableSeats());
        }

        Event updated = event.toBuilder()
                .bookedSeats(event.getBookedSeats() + request.seats())
                .build();
        events.save(EventDocument.from(updated));

        Booking booking = Booking.builder()
                .id(UUID.randomUUID().toString())
                .eventId(event.getId())
                .customerEmail(request.customerEmail())
                .seats(request.seats())
                .bookedAt(now)
                .build();
        bookings.save(BookingDocument.from(booking));

        return new BookingResult.Confirmed(booking);
    }

    public void addEvent(Event event) {
        throw new UnsupportedOperationException(LAB);
    }

    public Optional<Booking> findBooking(String id) {
        throw new UnsupportedOperationException(LAB);
    }

    public Optional<Event> findEvent(String id) {
        return events.findById(id).map(EventDocument::toDomain);
    }

    public List<Event> allEvents() {
        return events.findAll().stream()
                .map(EventDocument::toDomain)
                .toList();
    }

    public List<Event> eventsWithFreeSeats() {
        throw new UnsupportedOperationException(LAB);
    }

    public Map<String, Integer> bookedSeatsPerEvent() {
        throw new UnsupportedOperationException(LAB);
    }

    public List<Event> topEventsByBookedSeats(int n) {
        throw new UnsupportedOperationException(LAB);
    }

    public List<Event> upcomingEventsIn(String city, LocalDateTime now) {
        throw new UnsupportedOperationException(LAB);
    }
}
